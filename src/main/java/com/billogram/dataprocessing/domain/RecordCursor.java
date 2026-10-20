package com.billogram.dataprocessing.domain;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Position in the ordering of query results: the key of the last record of a page, after which the next page starts.
 */
public record RecordCursor(String destinationId, OffsetDateTime time, String recordId) {

    public RecordCursor {
        Objects.requireNonNull(destinationId, "destinationId");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(recordId, "recordId");
    }

    public static RecordCursor after(DataRecord record) {
        return new RecordCursor(record.destinationId(), record.time(), record.recordId());
    }
}
