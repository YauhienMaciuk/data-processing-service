package com.billogram.dataprocessing.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Builders for {@link DataRecord} test data. */
public final class TestRecords {

    public static final OffsetDateTime TIME = OffsetDateTime.parse("2026-10-20T10:00:00Z");

    private TestRecords() {
    }

    /** A valid positive EUR record for destination {@code dst-1}. */
    public static DataRecord record(String recordId) {
        return record(recordId, "dst-1", RecordType.POSITIVE, "1", "EUR");
    }

    public static DataRecord record(String recordId, String destinationId, RecordType type, String value, String unit) {
        return record(recordId, TIME, destinationId, type, value, unit);
    }

    public static DataRecord record(String recordId, OffsetDateTime time, String destinationId, RecordType type,
                                    String value, String unit) {
        return new DataRecord(recordId, time, "src-1", destinationId, type, new BigDecimal(value), unit, null);
    }

    /** A copy of {@code record} with the given reference. */
    public static DataRecord withReference(DataRecord record, String reference) {
        return new DataRecord(record.recordId(), record.time(), record.sourceId(), record.destinationId(),
                record.type(), record.value(), record.unit(), reference);
    }
}
