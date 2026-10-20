package com.billogram.dataprocessing.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlertPropertiesTest {

    private static AlertProperties bind(Map<String, String> source) {
        return new Binder(new MapConfigurationPropertySource(source)).bind("records.alerts", AlertProperties.class).get();
    }

    @Test
    void bindsTopicAndThresholdsKeepingUnitCaseWithBracketNotation() {
        AlertProperties properties = bind(Map.of(
                "records.alerts.topic", "alerts",
                "records.alerts.thresholds[EUR]", "10000",
                "records.alerts.thresholds[kg]", "1000.5"));

        assertEquals(new AlertProperties("alerts", Map.of("EUR", new BigDecimal("10000"), "kg", new BigDecimal("1000.5"))),
                properties);
    }

    @Test
    void missingThresholdsMeanNone() {
        assertEquals(Map.of(), bind(Map.of("records.alerts.topic", "alerts")).thresholds());
    }

    @Test
    void keepsAnImmutableCopyOfThresholds() {
        Map<String, BigDecimal> thresholds = new HashMap<>(Map.of("EUR", BigDecimal.TEN));
        AlertProperties properties = new AlertProperties("alerts", thresholds);

        thresholds.put("kg", BigDecimal.ONE);

        assertEquals(Map.of("EUR", BigDecimal.TEN), properties.thresholds());
        assertThrows(UnsupportedOperationException.class, () -> properties.thresholds().put("L", BigDecimal.ONE));
    }
}
