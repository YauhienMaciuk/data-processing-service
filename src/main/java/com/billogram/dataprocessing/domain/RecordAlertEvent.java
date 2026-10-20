package com.billogram.dataprocessing.domain;

import java.math.BigDecimal;

/**
 * Published when a processed record's value is above the configured threshold for its unit.
 *
 * @param record    the processed record
 * @param threshold the threshold its value exceeded, in the record's unit
 */
public record RecordAlertEvent(DataRecord record, BigDecimal threshold) {
}
