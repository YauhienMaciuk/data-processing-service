package com.billogram.dataprocessing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecordTypeTest {

    @Test
    void exposesLowercaseValues() {
        assertEquals("positive", RecordType.POSITIVE.value());
        assertEquals("negative", RecordType.NEGATIVE.value());
    }

    @Test
    void fromValueResolvesKnownValues() {
        assertEquals(RecordType.POSITIVE, RecordType.fromValue("positive"));
        assertEquals(RecordType.NEGATIVE, RecordType.fromValue("negative"));
    }

    @Test
    void fromValueRejectsUnknownOrDifferentlyCasedValues() {
        assertThrows(IllegalArgumentException.class, () -> RecordType.fromValue("neutral"));
        assertThrows(IllegalArgumentException.class, () -> RecordType.fromValue("POSITIVE"));
        assertThrows(IllegalArgumentException.class, () -> RecordType.fromValue(null));
    }

    @Test
    void positiveKeepsTheAmountAndNegativeNegatesIt() {
        assertEquals(new BigDecimal("12.50"), RecordType.POSITIVE.signed(new BigDecimal("12.50")));
        assertEquals(new BigDecimal("-12.50"), RecordType.NEGATIVE.signed(new BigDecimal("12.50")));
    }
}
