--liquibase formatted sql

--changeset data-processing-service:records-002-add-destination-time-index
-- Keyset pagination of GET /records: matches ORDER BY destination_id, time, record_id (skip-scanned for time
-- windows). The included columns let the totals query read only the index.
CREATE INDEX idx_records_destination_time ON records (destination_id, time, record_id) INCLUDE (unit, type, value);
--rollback DROP INDEX idx_records_destination_time;
