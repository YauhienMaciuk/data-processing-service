package com.billogram.dataprocessing.messaging;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings under {@code records.kafka.*}; all values are set in {@code application.properties}.
 *
 * @param topic           topic records are consumed from
 * @param deadLetterTopic topic that receives messages which could not be processed
 * @param retry           retries for failures that may be temporary (e.g. database unavailable)
 */
@Validated
@ConfigurationProperties("records.kafka")
public record RecordKafkaProperties(
        @NotBlank String topic,
        @NotBlank String deadLetterTopic,
        @NotNull @Valid Retry retry
) {

    /**
     * @param interval   delay between attempts
     * @param maxRetries attempts after the first failure before the message goes to the dead-letter topic
     */
    public record Retry(@NotNull Duration interval, @PositiveOrZero int maxRetries) {
    }
}
