package com.billogram.dataprocessing.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public enum RecordType {
    @JsonProperty("positive") POSITIVE("positive"),
    @JsonProperty("negative") NEGATIVE("negative");

    private final String value;

    RecordType(String value) {
        this.value = value;
    }

    /** Value used in JSON, query parameters and the {@code records.type} column. */
    public String value() {
        return value;
    }

    /** Applies the type as a sign: negative records count against a total. */
    public BigDecimal signed(BigDecimal amount) {
        return this == NEGATIVE ? amount.negate() : amount;
    }

    public static RecordType fromValue(String value) {
        for (RecordType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown record type: " + value + " (expected 'positive' or 'negative')");
    }
}
