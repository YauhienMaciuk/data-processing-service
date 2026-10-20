package com.billogram.dataprocessing.persistence;

import com.billogram.dataprocessing.IntegrationTest;
import com.billogram.dataprocessing.domain.RecordSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each call runs in its own transaction, as when a record is processed. That concurrent transactions wait for each
 * other's lock is covered end to end by {@code RecordNotificationIT}.
 */
@IntegrationTest
class RecordSummaryRepositoryIT {

    @Autowired
    private RecordSummaryRepository summaries;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        jdbc.update("DELETE FROM record_summaries");
    }

    private static OffsetDateTime at(String time) {
        return OffsetDateTime.parse("2026-10-20T" + time + ":00Z");
    }

    private static RecordSummary summaryOf(long count) {
        return new RecordSummary(count, Map.of("EUR", BigDecimal.valueOf(count)), at("09:00"), at("10:00"));
    }

    private RecordSummary lock(String destinationId, String reference) {
        return transaction.execute(status -> summaries.lock(destinationId, reference));
    }

    private void replace(String destinationId, String reference, RecordSummary summary) {
        transaction.executeWithoutResult(status -> {
            summaries.lock(destinationId, reference);
            summaries.update(destinationId, reference, summary);
        });
    }

    private int rows() {
        return jdbc.queryForObject("SELECT count(*) FROM record_summaries", Integer.class);
    }

    @Test
    void startsAnEmptySummaryForANewDestinationAndReference() {
        assertEquals(RecordSummary.empty(), lock("dst-1", "ref-1"));
        assertEquals(1, rows());
    }

    @Test
    void lockReturnsTheLastUpdateWithExactTotalsAndTimesInUtc() {
        Map<String, BigDecimal> totals = Map.of("EUR", new BigDecimal("12345.678901234567890"), "kg", new BigDecimal("-2.5"));
        replace("dst-1", "ref-1", new RecordSummary(3, totals, OffsetDateTime.parse("2026-10-20T10:00:00+02:00"), at("11:00")));

        assertEquals(new RecordSummary(3, totals, at("08:00"), at("11:00")), lock("dst-1", "ref-1"));
    }

    @Test
    void keepsOneSummaryPerDestinationAndReferenceWithRecordsWithoutReferenceSharingOne() {
        replace("dst-1", "ref-1", summaryOf(1));
        replace("dst-1", null, summaryOf(2));
        replace("dst-2", "ref-1", summaryOf(3));

        assertEquals(summaryOf(1), lock("dst-1", "ref-1"));
        assertEquals(summaryOf(2), lock("dst-1", null));
        assertEquals(summaryOf(3), lock("dst-2", "ref-1"));
        assertEquals(RecordSummary.empty(), lock("dst-2", null));
        assertEquals(4, rows());
    }
}
