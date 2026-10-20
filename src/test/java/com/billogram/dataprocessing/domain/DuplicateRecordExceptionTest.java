package com.billogram.dataprocessing.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DuplicateRecordExceptionTest {

    @Test
    void exposesRecordIdAndDescriptiveMessage() {
        DuplicateRecordException exception = new DuplicateRecordException("rec-1");

        assertEquals("rec-1", exception.getRecordId());
        assertEquals("Record with recordId 'rec-1' has already been processed", exception.getMessage());
    }
}
