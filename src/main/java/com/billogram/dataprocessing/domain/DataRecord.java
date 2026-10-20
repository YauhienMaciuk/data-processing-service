package com.billogram.dataprocessing.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** A record as received over Kafka or HTTP. Size limits match the {@code records} table columns. */
public record DataRecord(
        @NotBlank @Size(max = 255) String recordId,
        @NotNull OffsetDateTime time,
        @NotBlank @Size(max = 255) String sourceId,
        @NotBlank @Size(max = 255) String destinationId,
        @NotNull RecordType type,
        @NotNull BigDecimal value,
        @NotBlank @Size(max = 64) String unit,
        @Size(max = 255) String reference
) {
}
