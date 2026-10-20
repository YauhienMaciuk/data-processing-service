package com.billogram.dataprocessing.messaging;

import java.util.Map;

/** Thrown when a message holds no {@link com.billogram.dataprocessing.domain.DataRecord} or one that breaks its constraints. */
public class InvalidRecordException extends RuntimeException {

    private final Map<String, String> errors;

    public InvalidRecordException(Map<String, String> errors) {
        super("Invalid record: " + errors);
        this.errors = Map.copyOf(errors);
    }

    /** Field name → error message. */
    public Map<String, String> errors() {
        return errors;
    }
}
