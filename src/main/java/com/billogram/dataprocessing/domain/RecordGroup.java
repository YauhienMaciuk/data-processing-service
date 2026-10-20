package com.billogram.dataprocessing.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * One destination's records on a page of query results, with the signed total value per unit of all its records
 * matching the filter, including those on other pages (positive records add, negative records subtract).
 */
public record RecordGroup(String destinationId, Map<String, BigDecimal> totals, List<DataRecord> records) {
}
