package com.billogram.dataprocessing.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Alert thresholds per unit: a record alerts when its value, regardless of type, is strictly above the threshold
 * for its unit. Units are matched case-insensitively; units without a threshold never alert, since one number
 * can't be meaningful for different units.
 */
public final class ValueThresholds {

    private final Map<String, BigDecimal> thresholdByUnit = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    /**
     * @throws IllegalArgumentException if the same unit is given more than once, e.g. as "EUR" and "eur"
     */
    public ValueThresholds(Map<String, BigDecimal> thresholdByUnit) {
        thresholdByUnit.forEach((unit, threshold) -> {
            if (this.thresholdByUnit.putIfAbsent(unit, threshold) != null) {
                throw new IllegalArgumentException("Threshold for unit '" + unit + "' is configured more than once");
            }
        });
    }

    /** Returns the threshold the record's value exceeds, or empty if it doesn't exceed one. */
    public Optional<BigDecimal> exceededBy(DataRecord record) {
        return Optional.ofNullable(thresholdByUnit.get(record.unit()))
                .filter(threshold -> record.value().compareTo(threshold) > 0);
    }
}
