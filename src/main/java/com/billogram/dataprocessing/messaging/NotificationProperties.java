package com.billogram.dataprocessing.messaging;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings under {@code records.notifications.*}; all values are set in {@code application.properties}.
 *
 * @param topic Kafka topic the Notification service consumes record events from
 */
@Validated
@ConfigurationProperties("records.notifications")
public record NotificationProperties(@NotBlank String topic) {
}
