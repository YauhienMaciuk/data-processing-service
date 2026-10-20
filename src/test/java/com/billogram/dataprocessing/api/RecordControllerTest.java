package com.billogram.dataprocessing.api;

import com.billogram.dataprocessing.domain.DataRecord;
import com.billogram.dataprocessing.domain.DuplicateRecordException;
import com.billogram.dataprocessing.domain.RecordCursor;
import com.billogram.dataprocessing.domain.RecordFilter;
import com.billogram.dataprocessing.domain.RecordGroup;
import com.billogram.dataprocessing.domain.RecordPage;
import com.billogram.dataprocessing.domain.RecordQuery;
import com.billogram.dataprocessing.domain.RecordType;
import com.billogram.dataprocessing.domain.TestRecords;
import com.billogram.dataprocessing.service.RecordService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static com.billogram.dataprocessing.domain.TestRecords.record;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordControllerTest {

    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_LIMIT = 5;
    private static final RecordFilter FILTER = new RecordFilter(null, null, RecordType.POSITIVE);
    private static final RecordPageRequest FIRST_PAGE = new RecordPageRequest(null, null);
    private static final List<RecordGroup> GROUPS = List.of(new RecordGroup("dst-1", Map.of(), List.of(record("r1"))));

    private final RecordService recordService = mock(RecordService.class);
    private final RecordCursorCodec cursorCodec = new RecordCursorCodec(JsonMapper.builder().build());
    private final RecordController controller = new RecordController(recordService,
            new RecordProperties(new RecordProperties.Query(DEFAULT_LIMIT, MAX_LIMIT)), cursorCodec);

    private static void assertBadRequest(String expectedReason, Runnable call) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, call::run);
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(expectedReason, exception.getReason());
    }

    // --- POST /records

    @Test
    void processHandsRecordToServiceAndReturnsIt() {
        DataRecord record = record("r1");

        assertSame(record, controller.process(record));
        verify(recordService).process(record);
    }

    @Test
    void processPropagatesDuplicateRejection() {
        doThrow(new DuplicateRecordException("r1")).when(recordService).process(any());

        assertThrows(DuplicateRecordException.class, () -> controller.process(record("r1")));
    }

    // --- GET /records

    private void givenPage(RecordCursor next) {
        when(recordService.findGroupedByDestination(any())).thenReturn(new RecordPage(GROUPS, next));
    }

    @Test
    void queryAsksForFirstPageWithDefaultLimit() {
        givenPage(null);

        controller.query(FILTER, FIRST_PAGE);

        verify(recordService).findGroupedByDestination(new RecordQuery(FILTER, DEFAULT_LIMIT, null));
    }

    @Test
    void queryPassesRequestedLimitAndDecodedCursor() {
        RecordCursor cursor = RecordCursor.after(record("r1"));
        givenPage(null);

        controller.query(FILTER, new RecordPageRequest(MAX_LIMIT, cursorCodec.encode(cursor)));

        verify(recordService).findGroupedByDestination(new RecordQuery(FILTER, MAX_LIMIT, cursor));
    }

    @Test
    void queryReturnsGroupsWithEncodedCursorOfNextPage() {
        RecordCursor next = RecordCursor.after(record("r1"));
        givenPage(next);

        RecordGroupsResponse response = controller.query(FILTER, FIRST_PAGE);

        assertEquals(GROUPS, response.groups());
        assertEquals(next, cursorCodec.decode(response.nextCursor()));
    }

    @Test
    void queryReturnsNoCursorOnLastPage() {
        givenPage(null);

        assertEquals(new RecordGroupsResponse(GROUPS, null), controller.query(FILTER, FIRST_PAGE));
    }

    @Test
    void queryRejectsLimitOutsideOneToMaximum() {
        assertBadRequest("limit must be between 1 and 5",
                () -> controller.query(FILTER, new RecordPageRequest(0, null)));
        assertBadRequest("limit must be between 1 and 5",
                () -> controller.query(FILTER, new RecordPageRequest(MAX_LIMIT + 1, null)));
        verify(recordService, never()).findGroupedByDestination(any());
    }

    @Test
    void queryRejectsCursorItDidNotIssue() {
        assertBadRequest("cursor must be a nextCursor returned by this endpoint",
                () -> controller.query(FILTER, new RecordPageRequest(null, "not-a-cursor")));
        verify(recordService, never()).findGroupedByDestination(any());
    }

    @Test
    void queryRejectsInvalidTimeRange() {
        OffsetDateTime ten = OffsetDateTime.parse("2026-10-20T10:00:00Z");
        RecordFilter endBeforeStart = new RecordFilter(ten, ten.minusHours(1), null);

        assertBadRequest("endTime must be after startTime", () -> controller.query(endBeforeStart, FIRST_PAGE));
        verify(recordService, never()).findGroupedByDestination(any());
    }
}
