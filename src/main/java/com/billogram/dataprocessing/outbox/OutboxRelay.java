package com.billogram.dataprocessing.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes outbox messages to Kafka one at a time, in the order they were written, deleting each one once the broker
 * confirms it. Messages are taken in batches, each in its own transaction; while batches come back full, the next one
 * starts right away, so a backlog drains without waiting for the next scheduled run.
 * If a send fails, the run stops so that message and the ones after it are retried, still in order, on the next run.
 * Delivery is at-least-once: a crash after sending but before the delete commits republishes the message.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;
    private final TransactionTemplate transaction;

    public OutboxRelay(OutboxRepository repository, KafkaTemplate<String, String> kafkaTemplate,
                       OutboxProperties properties, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        // Programmatic rather than @Transactional, because one scheduled run spans several transactions, one per batch
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${outbox.relay.interval}")
    public void publishPending() {
        boolean batchWasFull;
        do {
            batchWasFull = Boolean.TRUE.equals(transaction.execute(status -> publishBatch()));
        } while (batchWasFull);
    }

    /** @return {@code true} if a full batch was published, so more messages may be waiting */
    private boolean publishBatch() {
        List<OutboxMessage> batch = repository.lockOldest(properties.batchSize());
        for (OutboxMessage message : batch) {
            if (!send(message)) {
                return false;
            }
            repository.delete(message.id());
        }
        return batch.size() == properties.batchSize();
    }

    private boolean send(OutboxMessage message) {
        try {
            kafkaTemplate.send(message.topic(), message.key(), message.payload())
                    .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while publishing outbox message {}; it will be retried", message.id());
            return false;
        } catch (ExecutionException | TimeoutException | KafkaException e) {
            // KafkaException: the template throws it when Kafka rejects a send right away (e.g. no topic metadata in time).
            // Letting it escape would roll back the deletes of the messages already sent, and they would be sent again.
            log.warn("Failed to publish outbox message {} to topic '{}'; it will be retried", message.id(), message.topic(), e);
            return false;
        }
    }
}
