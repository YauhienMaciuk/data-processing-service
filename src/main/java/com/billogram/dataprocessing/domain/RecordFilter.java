package com.billogram.dataprocessing.domain;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME;

/**
 * Optional filters for querying records; {@code null} means "no filter".
 * {@code startTime} is inclusive, {@code endTime} exclusive.
 */
public record RecordFilter(
        @DateTimeFormat(iso = DATE_TIME) OffsetDateTime startTime,
        @DateTimeFormat(iso = DATE_TIME) OffsetDateTime endTime,
        RecordType type
) {

    public boolean hasValidTimeRange() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }
}
