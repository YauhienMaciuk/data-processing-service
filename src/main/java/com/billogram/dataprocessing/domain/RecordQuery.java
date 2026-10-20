package com.billogram.dataprocessing.domain;

/**
 * A page of records to find: the records matching {@code filter}, ordered by destination id, then time, then record
 * id; at most {@code limit} of them, starting after {@code after}.
 *
 * @param after position the page starts after, {@code null} for the first page
 */
public record RecordQuery(RecordFilter filter, int limit, RecordCursor after) {

    public RecordQuery withLimit(int newLimit) {
        return new RecordQuery(filter, newLimit, after);
    }
}
