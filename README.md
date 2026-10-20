# Data processing service

Consumes data records from several services, processes each one exactly once, answers aggregation queries, and
sends a notification for every processed record plus an alert when a value is above a threshold.

## Run

Requires Docker (and JDK 27 to build or test outside Docker).

```bash
docker compose up --build   # Postgres :5432, Kafka :9092, service :8080
./gradlew check             # unit and integration tests (real Postgres and Kafka via Testcontainers), 100% coverage gate
```

Try it:

```bash
# A record through Kafka, the main input
docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic records <<'EOF'
{"recordId":"r-1","time":"2026-10-20T10:00:00Z","sourceId":"src-1","destinationId":"dst-1","type":"positive","value":12.50,"unit":"EUR","reference":"inv-1"}
EOF

# A record over HTTP
curl -i localhost:8080/records -H 'Content-Type: application/json' \
  -d '{"recordId":"r-2","time":"2026-10-20T11:00:00Z","sourceId":"src-1","destinationId":"dst-1","type":"negative","value":2.50,"unit":"EUR","reference":"inv-1"}'

# The aggregation query
curl 'localhost:8080/records?startTime=2026-10-20T00:00:00Z&limit=50'

# The events for the Notification service
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic record-notifications --from-beginning
```

## How it works

```
  Data parsing ──► Kafka "records" ──┐
  POST /records ─────────────────────┤
                                     ▼
                      process record: one transaction
           (insert record, update running summary, write outbox)
                                     │
  Reporting ──► GET /records ──► Postgres
                                     │
                               outbox relay
                                     ├──► Kafka "record-notifications" ──► Notification
                                     └──► Kafka "record-alerts" ─────────► Alerting
```

Each record is processed in one database transaction:

1. Insert the record. If its `recordId` was processed before, stop: it's a duplicate.
2. Lock and update the running summary of its `destinationId` and `reference`.
3. Write the notification, and an alert if the value is above the threshold, to the outbox table.

The outbox relay sends the stored events to Kafka in order and deletes each one once Kafka confirms it.

## API

| Endpoint        | Description                                                                                                                                                                                                   |
|-----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `POST /records` | One record: `201` stored, `400` invalid, `409` `recordId` already processed. Kafka is the main input; this is for testing and producers that can't use Kafka.                                                 |
| `GET /records`  | Optional filters `startTime` (inclusive), `endTime` (exclusive), `type`; `limit` (default 100, max 1000); `cursor` = `nextCursor` of the previous page.                                                       |

`GET /records` returns the matching records in time order, grouped by `destinationId`. Each group has a signed total per unit, which
covers all matching records, not just the ones on this page.

Kafka output, keyed by `destinationId`:

- `record-notifications`: `{"record": {...}, "previousRecords": {"count": 2, "totals": {"EUR": 70.25}, "firstTime": "...", "lastTime": "..."}}`
- `record-alerts`: `{"record": {...}, "threshold": 10000}`

Invalid input messages go to `records-dlt`.

## Design choices

| Choice                                                     | Why                                                                                                                                                                                                                                                              |
|------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Kafka for input and output                                 | The inputs and outputs in the diagram are messages. Kafka keeps them in order per key and holds them while either side is down.                                                                                                                                 |
| PostgreSQL                                                 | Storing a record and its events must be all-or-nothing, which needs transactions. SQL handles the aggregation query. About 28 records/s fits one instance easily.                                                                                              |
| `record_id` primary key + `INSERT … ON CONFLICT DO NOTHING` | Idempotency in one atomic statement: a redelivered or concurrent duplicate is skipped, so each record is processed once.                                                                                                                                        |
| One transaction per record                                 | The record, its summary and its events are saved together or not at all. The Kafka offset is committed afterwards: after a crash the record is delivered again and skipped as a duplicate.                                                                     |
| Transactional outbox                                       | No event is lost, and none is sent for a rolled-back record. The relay sends one message at a time, so order is kept.                                                                                                                                          |
| Running summary table                                      | The "previous records" summary is one row per destination and reference, locked and updated with each record. It costs about 1 ms however long the history is, and stays correct when records arrive together. Re-reading the history took 59 ms at 1M records and gave wrong counts under concurrency. |
| Cursor (keyset) pagination, totals in SQL                  | A page takes about 0.05 ms, however deep. Totals are computed over all matching records, in the same database snapshot as the page.                                                                                                                             |
| Only the indexes the queries use                           | Checked against real query plans on one day of data (2.4M records). Every query uses an index, and no unused index slows down inserts.                                                                                                                          |
| `BigDecimal` and `NUMERIC`                                 | Amounts stay exact.                                                                                                                                                                                                                                              |
| Tests against real Postgres and Kafka                      | Testcontainers runs the same versions as docker-compose. Unit tests cover the logic; 100% line and branch coverage is enforced.                                                                                                                                |
