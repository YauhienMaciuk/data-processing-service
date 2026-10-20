package com.billogram.dataprocessing.api;

import com.billogram.dataprocessing.domain.DataRecord;
import com.billogram.dataprocessing.domain.RecordCursor;
import com.billogram.dataprocessing.domain.RecordFilter;
import com.billogram.dataprocessing.domain.RecordPage;
import com.billogram.dataprocessing.domain.RecordQuery;
import com.billogram.dataprocessing.service.RecordService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/** HTTP API for submitting and querying records. Error responses are produced by {@link ApiExceptionHandler}. */
@RestController
@RequestMapping("/records")
public class RecordController {

    private final RecordService recordService;
    private final RecordCursorCodec cursorCodec;
    private final int defaultLimit;
    private final int maxLimit;

    public RecordController(RecordService recordService, RecordProperties properties, RecordCursorCodec cursorCodec) {
        this.recordService = recordService;
        this.cursorCodec = cursorCodec;
        this.defaultLimit = properties.query().defaultLimit();
        this.maxLimit = properties.query().maxLimit();
    }

    /**
     * Processes a single record: 201 Created on success, 400 if invalid,
     * 409 Conflict if a record with the same {@code recordId} has already been processed.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DataRecord process(@Valid @RequestBody DataRecord record) {
        recordService.process(record);
        return record;
    }

    /**
     * Returns one page of the processed records matching the optional filters, grouped by destination id, each group
     * with totals per unit over all its matching records. Records are ordered by destination id, then time, then
     * record id. For the next page, repeat the request with {@code cursor} set to the response's {@code nextCursor},
     * which is {@code null} on the last page.
     */
    @GetMapping
    public RecordGroupsResponse query(@ModelAttribute RecordFilter filter, @ModelAttribute RecordPageRequest pageRequest) {
        requireValidTimeRange(filter);
        RecordPage page = recordService.findGroupedByDestination(toQuery(filter, pageRequest));
        return new RecordGroupsResponse(page.groups(), page.hasNext() ? cursorCodec.encode(page.next()) : null);
    }

    private RecordQuery toQuery(RecordFilter filter, RecordPageRequest pageRequest) {
        int limit = Objects.requireNonNullElse(pageRequest.limit(), defaultLimit);
        requireValidLimit(limit);
        return new RecordQuery(filter, limit, decodeCursor(pageRequest.cursor()));
    }

    private static void requireValidTimeRange(RecordFilter filter) {
        if (!filter.hasValidTimeRange()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endTime must be after startTime");
        }
    }

    private void requireValidLimit(int limit) {
        if (limit < 1 || limit > maxLimit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and " + maxLimit);
        }
    }

    /** Decodes the {@code cursor} parameter; no cursor means the first page. */
    private RecordCursor decodeCursor(String cursor) {
        if (cursor == null) {
            return null;
        }
        try {
            return cursorCodec.decode(cursor);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cursor must be a nextCursor returned by this endpoint");
        }
    }
}
