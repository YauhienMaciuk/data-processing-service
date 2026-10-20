package com.billogram.dataprocessing.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Summary of a set of records, built up one record at a time with {@link #including}.
 *
 * @param count     number of records
 * @param totals    signed total value per unit, sorted by unit (positive records add, negative subtract)
 * @param firstTime earliest record time, {@code null} when {@code count} is 0
 * @param lastTime  latest record time, {@code null} when {@code count} is 0
 */
public record RecordSummary(long count, Map<String, BigDecimal> totals, OffsetDateTime firstTime, OffsetDateTime lastTime) {

    public RecordSummary {
        totals = Collections.unmodifiableMap(new TreeMap<>(totals));
    }

    public static RecordSummary empty() {
        return new RecordSummary(0, Map.of(), null, null);
    }

    /** This summary with {@code record} added to it; the summary itself is left unchanged. */
    public RecordSummary including(DataRecord record) {
        Map<String, BigDecimal> newTotals = new TreeMap<>(totals);
        newTotals.merge(record.unit(), record.type().signed(record.value()), BigDecimal::add);
        return new RecordSummary(count + 1, newTotals, earlier(firstTime, record.time()), later(lastTime, record.time()));
    }

    private static OffsetDateTime earlier(OffsetDateTime current, OffsetDateTime candidate) {
        return current == null || candidate.isBefore(current) ? candidate : current;
    }

    private static OffsetDateTime later(OffsetDateTime current, OffsetDateTime candidate) {
        return current == null || candidate.isAfter(current) ? candidate : current;
    }
}
