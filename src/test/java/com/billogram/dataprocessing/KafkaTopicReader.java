package com.billogram.dataprocessing;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.ConsumerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.awaitility.Awaitility.await;

/**
 * Reads a Kafka topic from the beginning in integration tests, with its own consumer group so it doesn't
 * interfere with the application's consumers. Messages from earlier tests are also read, so filter by unique ids.
 */
public final class KafkaTopicReader implements AutoCloseable {

    public static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final Consumer<String, String> consumer;
    private final List<ConsumerRecord<String, String>> received = new ArrayList<>();

    public KafkaTopicReader(ConsumerFactory<String, String> consumerFactory, String topic) {
        this.consumer = consumerFactory.createConsumer("test-reader-" + UUID.randomUUID(), null);
        consumer.subscribe(List.of(topic));
    }

    /** Polls once more and returns every message received so far that matches. */
    public List<ConsumerRecord<String, String>> poll(Predicate<ConsumerRecord<String, String>> filter) {
        consumer.poll(Duration.ofMillis(500)).forEach(received::add);
        return received.stream().filter(filter).toList();
    }

    /** Waits until a matching message arrives and returns the first one. */
    public ConsumerRecord<String, String> awaitFirst(Predicate<ConsumerRecord<String, String>> filter) {
        return await().atMost(TIMEOUT).until(() -> poll(filter), messages -> !messages.isEmpty()).getFirst();
    }

    @Override
    public void close() {
        consumer.close();
    }
}
