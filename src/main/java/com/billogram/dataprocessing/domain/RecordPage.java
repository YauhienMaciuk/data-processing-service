package com.billogram.dataprocessing.domain;

import java.util.List;

/**
 * @param groups the page's records grouped by destination id, in result order
 * @param next   where the next page starts, {@code null} if this is the last page
 */
public record RecordPage(List<RecordGroup> groups, RecordCursor next) {

    public boolean hasNext() {
        return next != null;
    }
}
