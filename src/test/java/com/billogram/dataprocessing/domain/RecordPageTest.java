package com.billogram.dataprocessing.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.billogram.dataprocessing.domain.TestRecords.record;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordPageTest {

    @Test
    void hasNextWhenThereIsACursorForIt() {
        assertTrue(new RecordPage(List.of(), RecordCursor.after(record("r1"))).hasNext());
    }

    @Test
    void lastPageHasNoNext() {
        assertFalse(new RecordPage(List.of(), null).hasNext());
    }
}
