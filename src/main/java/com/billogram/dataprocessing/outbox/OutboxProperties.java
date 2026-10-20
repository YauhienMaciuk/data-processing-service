package com.billogram.dataprocessing.outbox;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings under {@code outbox.relay.*}; all values are set in {@code application.properties}.
 *
 * @param interval    pause before the next run once the outbox is drained or a send has failed
 * @param batchSize   maximum messages published per transaction; a full batch is followed by the next one right away
 * @param sendTimeout how long to wait for Kafka to confirm a message before retrying it on the next run; longer than
 *                    the producer's {@code delivery.timeout.ms}, so a message given up on is never delivered later
 */
@Validated
@ConfigurationProperties("outbox.relay")
public record OutboxProperties(@NotNull Duration interval, @Positive int batchSize, @NotNull Duration sendTimeout) {
}
