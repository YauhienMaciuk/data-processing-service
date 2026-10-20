package com.billogram.dataprocessing.domain;

/**
 * @param previousRecords summary of the records processed before it with the same destination id and reference
 *                        (records without a reference are summarized together)
 */
public record RecordProcessedEvent(DataRecord record, RecordSummary previousRecords) {
}
