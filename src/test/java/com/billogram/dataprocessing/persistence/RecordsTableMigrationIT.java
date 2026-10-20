package com.billogram.dataprocessing.persistence;

import com.billogram.dataprocessing.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies the Liquibase-created schema of the {@code records} table directly with SQL. */
@IntegrationTest
class RecordsTableMigrationIT {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM records");
    }

    private int insert(String recordId, String type, String reference) {
        return jdbc.update("""
                        INSERT INTO records (record_id, time, source_id, destination_id, type, value, unit, reference)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                recordId, Timestamp.from(Instant.parse("2026-10-20T08:00:00Z")), "src-1", "dst-1",
                type, new BigDecimal("12345.678901234567890"), "EUR", reference);
    }

    @Test
    void storesRecordWithExactDecimalAndCreationTime() {
        insert("rec-1", "positive", "ref-1");

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM records WHERE record_id = 'rec-1'");
        assertEquals(new BigDecimal("12345.678901234567890"), row.get("value"));
        assertEquals("positive", row.get("type"));
        assertNotNull(row.get("created_at"));
    }

    /** Every index costs time on each insert, so the table has only the ones the queries use. */
    @Test
    void hasThePrimaryKeyAndOnlyTheIndexesTheQueriesUse() {
        Map<String, String> indexes = new HashMap<>();
        jdbc.query("SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'records'", row -> {
            indexes.put(row.getString("indexname"), row.getString("indexdef"));
        });

        assertEquals(Map.of(
                "pk_records", "CREATE UNIQUE INDEX pk_records ON public.records USING btree (record_id)",
                "idx_records_destination_time", "CREATE INDEX idx_records_destination_time ON public.records "
                        + "USING btree (destination_id, \"time\", record_id) INCLUDE (unit, type, value)"), indexes);
    }

    @Test
    void referenceIsOptional() {
        assertEquals(1, insert("rec-2", "negative", null));
    }

    @Test
    void rejectsUnknownType() {
        assertThrows(DataIntegrityViolationException.class, () -> insert("rec-3", "neutral", null));
    }

    @Test
    void rejectsDuplicateRecordId() {
        insert("rec-4", "positive", null);
        assertThrows(DuplicateKeyException.class, () -> insert("rec-4", "positive", null));
    }
}
