package com.billogram.dataprocessing.outbox;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Stores outbox messages; writes take part in the caller's transaction. */
@Repository
public class OutboxRepository {

    private static final RowMapper<OutboxMessage> MESSAGE_MAPPER = (rs, rowNum) -> new OutboxMessage(
            rs.getLong("id"),
            rs.getString("topic"),
            rs.getString("message_key"),
            rs.getString("payload"));

    private final JdbcClient jdbc;

    public OutboxRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void add(String topic, String key, String payload) {
        jdbc.sql("INSERT INTO outbox_events (topic, message_key, payload) VALUES (:topic, :key, :payload)")
                .param("topic", topic)
                .param("key", key)
                .param("payload", payload)
                .update();
    }

    /**
     * Locks and returns the oldest pending messages, at most {@code limit}. Rows locked by another
     * transaction (e.g. another application instance) are skipped, so each message is published by one relay.
     * Must be called within a transaction; the locks are held until it ends.
     */
    public List<OutboxMessage> lockOldest(int limit) {
        return jdbc.sql("""
                        SELECT id, topic, message_key, payload
                        FROM outbox_events
                        ORDER BY id
                        LIMIT :limit
                        FOR UPDATE SKIP LOCKED""")
                .param("limit", limit)
                .query(MESSAGE_MAPPER)
                .list();
    }

    public void delete(long id) {
        jdbc.sql("DELETE FROM outbox_events WHERE id = :id").param("id", id).update();
    }
}
