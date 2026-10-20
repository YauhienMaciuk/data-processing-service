package com.billogram.dataprocessing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.billogram.dataprocessing.domain.TestRecords.record;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValueThresholdsTest {

    private final ValueThresholds thresholds = new ValueThresholds(Map.of(
            "EUR", new BigDecimal("10000"),
            "kg", new BigDecimal("1000")));

    private static DataRecord recordOf(String value, String unit, RecordType type) {
        return record("r1", "dst-1", type, value, unit);
    }

    @Test
    void valueAboveThresholdForItsUnitExceedsIt() {
        assertEquals(Optional.of(new BigDecimal("10000")),
                thresholds.exceededBy(recordOf("10000.01", "EUR", RecordType.POSITIVE)));
        assertEquals(Optional.of(new BigDecimal("1000")),
                thresholds.exceededBy(recordOf("1500", "kg", RecordType.POSITIVE)));
    }

    @Test
    void negativeTypeIsComparedByItsValueToo() {
        assertEquals(Optional.of(new BigDecimal("10000")),
                thresholds.exceededBy(recordOf("50000", "EUR", RecordType.NEGATIVE)));
    }

    @Test
    void valueAtOrBelowThresholdDoesNotExceedIt() {
        assertEquals(Optional.empty(), thresholds.exceededBy(recordOf("10000", "EUR", RecordType.POSITIVE)));
        assertEquals(Optional.empty(), thresholds.exceededBy(recordOf("10000.00", "EUR", RecordType.POSITIVE)));
        assertEquals(Optional.empty(), thresholds.exceededBy(recordOf("9999.99", "EUR", RecordType.POSITIVE)));
    }

    @Test
    void thresholdOfAnotherUnitDoesNotApply() {
        // 5000 is above the kg threshold, but these are litres
        assertEquals(Optional.empty(), thresholds.exceededBy(recordOf("5000", "L", RecordType.POSITIVE)));
    }

    @Test
    void unitsMatchCaseInsensitively() {
        assertEquals(Optional.of(new BigDecimal("10000")), thresholds.exceededBy(recordOf("20000", "eur", RecordType.POSITIVE)));
        assertEquals(Optional.of(new BigDecimal("1000")), thresholds.exceededBy(recordOf("2000", "KG", RecordType.POSITIVE)));
    }

    @Test
    void noThresholdsMeanNoAlerts() {
        assertEquals(Optional.empty(), new ValueThresholds(Map.of()).exceededBy(recordOf("1000000", "EUR", RecordType.POSITIVE)));
    }

    @Test
    void rejectsSameUnitConfiguredTwiceInDifferentCase() {
        Map<String, BigDecimal> duplicated = new LinkedHashMap<>();
        duplicated.put("EUR", BigDecimal.ONE);
        duplicated.put("eur", BigDecimal.TEN);

        assertThrows(IllegalArgumentException.class, () -> new ValueThresholds(duplicated));
    }
}
