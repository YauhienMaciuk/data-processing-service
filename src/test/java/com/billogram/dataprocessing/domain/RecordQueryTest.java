package com.billogram.dataprocessing.domain;

import org.junit.jupiter.api.Test;

import static com.billogram.dataprocessing.domain.TestRecords.TIME;
import static com.billogram.dataprocessing.domain.TestRecords.record;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordQueryTest {

    @Test
    void withLimitChangesOnlyTheLimit() {
        RecordFilter filter = new RecordFilter(TIME, null, RecordType.POSITIVE);
        RecordCursor after = RecordCursor.after(record("r1"));
        RecordQuery query = new RecordQuery(filter, 10, after);

        assertEquals(new RecordQuery(filter, 11, after), query.withLimit(11));
    }
}
