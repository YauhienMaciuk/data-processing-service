package com.billogram.dataprocessing.domain;

public class DuplicateRecordException extends RuntimeException {

    private final String recordId;

    public DuplicateRecordException(String recordId) {
        super("Record with recordId '" + recordId + "' has already been processed");
        this.recordId = recordId;
    }

    public String getRecordId() {
        return recordId;
    }
}
