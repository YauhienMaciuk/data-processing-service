package com.billogram.dataprocessing.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordFilterTest {

    private static final OffsetDateTime NINE = OffsetDateTime.parse("2026-10-20T09:00:00Z");
    private static final OffsetDateTime TEN = OffsetDateTime.parse("2026-10-20T10:00:00Z");

    private static RecordFilter range(OffsetDateTime start, OffsetDateTime end) {
        return new RecordFilter(start, end, null);
    }

    @Test
    void openOrHalfOpenRangesAreValid() {
        assertTrue(range(null, null).hasValidTimeRange());
        assertTrue(range(NINE, null).hasValidTimeRange());
        assertTrue(range(null, TEN).hasValidTimeRange());
    }

    @Test
    void endAfterStartIsValid() {
        assertTrue(range(NINE, TEN).hasValidTimeRange());
    }

    @Test
    void endEqualToOrBeforeStartIsInvalid() {
        assertFalse(range(NINE, NINE).hasValidTimeRange());
        assertFalse(range(TEN, NINE).hasValidTimeRange());
    }

    @Test
    void comparesInstantsRegardlessOfOffset() {
        // 11:00+02:00 is 09:00Z, i.e. before 10:00Z
        assertFalse(range(TEN, OffsetDateTime.parse("2026-10-20T11:00:00+02:00")).hasValidTimeRange());
    }
}
