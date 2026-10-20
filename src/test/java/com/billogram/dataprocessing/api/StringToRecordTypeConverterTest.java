package com.billogram.dataprocessing.api;

import com.billogram.dataprocessing.domain.RecordType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StringToRecordTypeConverterTest {

    private final StringToRecordTypeConverter converter = new StringToRecordTypeConverter();

    @Test
    void convertsLowercaseValues() {
        assertEquals(RecordType.POSITIVE, converter.convert("positive"));
        assertEquals(RecordType.NEGATIVE, converter.convert("negative"));
    }

    @Test
    void rejectsUnknownValues() {
        assertThrows(IllegalArgumentException.class, () -> converter.convert("neutral"));
    }
}
