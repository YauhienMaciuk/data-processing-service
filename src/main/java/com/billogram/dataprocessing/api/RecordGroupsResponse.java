package com.billogram.dataprocessing.api;

import com.billogram.dataprocessing.domain.RecordGroup;
import java.util.List;

/**
 * Response of {@code GET /records}: one page of matching records grouped by destination id.
 *
 * @param nextCursor pass as the {@code cursor} parameter to get the next page; {@code null} on the last page
 */
public record RecordGroupsResponse(List<RecordGroup> groups, String nextCursor) {
}
