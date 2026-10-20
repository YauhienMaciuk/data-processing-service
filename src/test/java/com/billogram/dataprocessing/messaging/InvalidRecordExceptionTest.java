package com.billogram.dataprocessing.messaging;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvalidRecordExceptionTest {

    @Test
    void exposesErrorsAndIncludesThemInMessage() {
        InvalidRecordException exception = new InvalidRecordException(Map.of("type", "invalid value"));

        assertEquals(Map.of("type", "invalid value"), exception.errors());
        assertTrue(exception.getMessage().contains("type=invalid value"), exception.getMessage());
    }

    @Test
    void keepsAnImmutableCopyOfTheErrors() {
        Map<String, String> errors = new HashMap<>(Map.of("type", "invalid value"));
        InvalidRecordException exception = new InvalidRecordException(errors);

        errors.put("time", "invalid value");

        assertEquals(Map.of("type", "invalid value"), exception.errors());
        assertThrows(UnsupportedOperationException.class, () -> exception.errors().put("x", "y"));
    }
}
