package com.billogram.dataprocessing.messaging;

import com.billogram.dataprocessing.domain.RecordType;
import com.billogram.dataprocessing.domain.ValueThresholds;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static com.billogram.dataprocessing.domain.TestRecords.record;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AlertConfigurationTest {

    private final AlertConfiguration configuration = new AlertConfiguration();
    private final AlertProperties properties = new AlertProperties("record-alerts", Map.of("EUR", new BigDecimal("10000")));

    @Test
    void declaresConfiguredTopic() {
        assertEquals("record-alerts", configuration.alertsTopic(properties).name());
    }

    @Test
    void createsThresholdsFromProperties() {
        ValueThresholds thresholds = configuration.alertThresholds(properties);

        assertEquals(Optional.of(new BigDecimal("10000")),
                thresholds.exceededBy(record("r1", "dst-1", RecordType.POSITIVE, "10001", "EUR")));
    }
}
