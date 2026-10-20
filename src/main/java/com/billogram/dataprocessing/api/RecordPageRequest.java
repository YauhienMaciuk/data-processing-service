package com.billogram.dataprocessing.api;

/**
 * @param limit  maximum number of records on the page; {@code null} for the configured default
 * @param cursor {@code nextCursor} of the previous page; {@code null} for the first page
 */
public record RecordPageRequest(Integer limit, String cursor) {
}
