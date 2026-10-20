package com.billogram.dataprocessing.domain;

import org.junit.jupiter.api.Test;

import static com.billogram.dataprocessing.domain.TestRecords.TIME;
import static com.billogram.dataprocessing.domain.TestRecords.record;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecordCursorTest {

    @Test
    void afterRecordHoldsItsKey() {
        DataRecord record = record("r1", "dst-1", RecordType.NEGATIVE, "12.50", "EUR");

        assertEquals(new RecordCursor("dst-1", TIME, "r1"), RecordCursor.after(record));
    }

    @Test
    void requiresEveryField() {
        assertThrows(NullPointerException.class, () -> new RecordCursor(null, TIME, "r1"));
        assertThrows(NullPointerException.class, () -> new RecordCursor("dst-1", null, "r1"));
        assertThrows(NullPointerException.class, () -> new RecordCursor("dst-1", TIME, null));
    }
}
