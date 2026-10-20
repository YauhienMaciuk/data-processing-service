package com.billogram.dataprocessing.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Settings under {@code records.alerts.*}; values are set in {@code application.properties}.
 *
 * @param topic      Kafka topic the alerting service consumes alerts from
 * @param thresholds alert threshold per unit, e.g. {@code records.alerts.thresholds[EUR]=10000};
 *                   units without a threshold never alert
 */
@Validated
@ConfigurationProperties("records.alerts")
public record AlertProperties(
        @NotBlank String topic,
        Map<String, @NotNull @PositiveOrZero BigDecimal> thresholds
) {

    public AlertProperties {
        thresholds = thresholds == null ? Map.of() : Map.copyOf(thresholds);
    }
}
