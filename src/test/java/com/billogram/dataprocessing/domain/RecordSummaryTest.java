package com.billogram.dataprocessing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.billogram.dataprocessing.domain.TestRecords.record;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecordSummaryTest {

    private static final RecordSummary TWO_EUR_RECORDS =
            new RecordSummary(2, Map.of("EUR", new BigDecimal("70.25")), at("09:00"), at("10:00"));

    private static OffsetDateTime at(String time) {
        return OffsetDateTime.parse("2026-10-20T" + time + ":00Z");
    }

    private static DataRecord recordAt(String time, RecordType type, String value, String unit) {
        return record("r1", at(time), "dst-1", type, value, unit);
    }

    @Test
    void emptySummaryHasNoRecords() {
        assertEquals(new RecordSummary(0, Map.of(), null, null), RecordSummary.empty());
    }

    @Test
    void includingTheFirstRecordStartsTheSummary() {
        assertEquals(new RecordSummary(1, Map.of("EUR", new BigDecimal("-12.50")), at("09:00"), at("09:00")),
                RecordSummary.empty().including(recordAt("09:00", RecordType.NEGATIVE, "12.50", "EUR")));
    }

    @Test
    void includingAddsTheSignedValueToTheTotalOfItsUnit() {
        assertEquals(Map.of("EUR", new BigDecimal("75.25")),
                TWO_EUR_RECORDS.including(recordAt("09:30", RecordType.POSITIVE, "5", "EUR")).totals());
        assertEquals(Map.of("EUR", new BigDecimal("70.00")),
                TWO_EUR_RECORDS.including(recordAt("09:30", RecordType.NEGATIVE, "0.25", "EUR")).totals());
        assertEquals(Map.of("EUR", new BigDecimal("70.25"), "kg", new BigDecimal("2")),
                TWO_EUR_RECORDS.including(recordAt("09:30", RecordType.POSITIVE, "2", "kg")).totals());
    }

    @Test
    void includingCountsTheRecordAndWidensTheTimeRangeOnlyWhenOutsideIt() {
        RecordSummary earlier = TWO_EUR_RECORDS.including(recordAt("08:00", RecordType.POSITIVE, "1", "EUR"));
        RecordSummary later = TWO_EUR_RECORDS.including(recordAt("11:00", RecordType.POSITIVE, "1", "EUR"));
        RecordSummary inBetween = TWO_EUR_RECORDS.including(recordAt("09:30", RecordType.POSITIVE, "1", "EUR"));

        assertEquals(3, inBetween.count());
        assertEquals(List.of(at("08:00"), at("10:00")), List.of(earlier.firstTime(), earlier.lastTime()));
        assertEquals(List.of(at("09:00"), at("11:00")), List.of(later.firstTime(), later.lastTime()));
        assertEquals(List.of(at("09:00"), at("10:00")), List.of(inBetween.firstTime(), inBetween.lastTime()));
    }

    @Test
    void includingLeavesTheOriginalSummaryUnchanged() {
        TWO_EUR_RECORDS.including(recordAt("08:00", RecordType.POSITIVE, "5", "kg"));

        assertEquals(new RecordSummary(2, Map.of("EUR", new BigDecimal("70.25")), at("09:00"), at("10:00")),
                TWO_EUR_RECORDS);
    }

    @Test
    void keepsTotalsSortedByUnitAndImmutable() {
        Map<String, BigDecimal> totals = new HashMap<>(Map.of("kg", BigDecimal.ONE, "EUR", BigDecimal.TEN, "L", BigDecimal.TWO));

        RecordSummary summary = new RecordSummary(3, totals, at("08:00"), at("09:00"));
        totals.clear();

        assertEquals(List.of("EUR", "L", "kg"), List.copyOf(summary.totals().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> summary.totals().put("x", BigDecimal.ONE));
    }
}
