package com.billogram.dataprocessing.persistence;

import com.billogram.dataprocessing.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies the Liquibase-created schema of the {@code record_summaries} table directly with SQL. */
@IntegrationTest
class RecordSummariesTableMigrationIT {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM record_summaries");
    }

    private void insertSummary(String destinationId, String reference) {
        jdbc.update("INSERT INTO record_summaries (destination_id, reference, record_count, totals) VALUES (?, ?, 0, '{}')",
                destinationId, reference);
    }

    @Test
    void allowsOneSummaryPerDestinationAndReferenceCountingNoReferenceAsOne() {
        insertSummary("dst-1", "ref-1");
        insertSummary("dst-1", null);
        insertSummary("dst-2", null);

        assertThrows(DuplicateKeyException.class, () -> insertSummary("dst-1", "ref-1"));
        assertThrows(DuplicateKeyException.class, () -> insertSummary("dst-1", null));
    }
}
