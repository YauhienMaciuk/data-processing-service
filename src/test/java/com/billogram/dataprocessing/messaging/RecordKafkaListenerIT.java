package com.billogram.dataprocessing.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import com.billogram.dataprocessing.IntegrationTest;
import com.billogram.dataprocessing.KafkaTopicReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static com.billogram.dataprocessing.KafkaTopicReader.TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end tests of consuming records: Kafka → JSON → validation → Postgres, failures → dead-letter topic. */
@IntegrationTest
class RecordKafkaListenerIT {

    /** Same key for every message, so messages land on one partition and are consumed in send order. */
    private static final String KEY = "it";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private RecordKafkaProperties properties;

    @Autowired
    private JdbcTemplate jdbc;

    private KafkaTopicReader deadLetters;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM records");
        deadLetters = new KafkaTopicReader(consumerFactory, properties.deadLetterTopic());
    }

    @AfterEach
    void tearDown() {
        deadLetters.close();
    }

    private static String uniqueId() {
        return "kafka-" + UUID.randomUUID();
    }

    private static String record(String recordId, String sourceId) {
        return """
                {"recordId": "%s", "time": "2026-10-20T10:00:00Z", "sourceId": "%s", "destinationId": "dst-1",
                 "type": "positive", "value": 12345.678901234567890, "unit": "EUR"}""".formatted(recordId, sourceId);
    }

    private void publish(String message) {
        kafkaTemplate.send(properties.topic(), KEY, message).join();
    }

    private int rowsFor(String recordId) {
        return jdbc.queryForObject("SELECT count(*) FROM records WHERE record_id = ?", Integer.class, recordId);
    }

    private void awaitStored(String recordId) {
        await().atMost(TIMEOUT).until(() -> rowsFor(recordId) == 1);
    }

    private ConsumerRecord<String, String> awaitDeadLetter(String payload) {
        return deadLetters.awaitFirst(message -> payload.equals(message.value()));
    }

    private static String header(ConsumerRecord<String, String> message, String name) {
        Header header = message.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    @Test
    void storesValidRecord() {
        String recordId = uniqueId();

        publish(record(recordId, "src-1"));

        awaitStored(recordId);
        assertEquals(new BigDecimal("12345.678901234567890"),
                jdbc.queryForObject("SELECT value FROM records WHERE record_id = ?", BigDecimal.class, recordId));
    }

    @Test
    void skipsRedeliveredRecordWithoutDeadLettering() {
        String recordId = uniqueId();
        String duplicate = record(recordId, "src-2");
        String marker = uniqueId();
        publish(record(recordId, "src-1"));
        awaitStored(recordId);

        publish(duplicate);
        publish(record(marker, "src-1"));

        // The marker is consumed after the duplicate, so once it's stored the duplicate has been handled
        awaitStored(marker);
        assertEquals(1, rowsFor(recordId));
        assertEquals("src-1", jdbc.queryForObject("SELECT source_id FROM records WHERE record_id = ?", String.class, recordId));
        assertTrue(deadLetters.poll(message -> duplicate.equals(message.value())).isEmpty(),
                "duplicate must not be dead-lettered");
    }

    @Test
    void sendsInvalidRecordToDeadLetterTopic() {
        String recordId = uniqueId();
        String invalid = """
                {"recordId": "%s", "type": "positive", "value": 1, "unit": "EUR"}""".formatted(recordId);

        publish(invalid);

        ConsumerRecord<String, String> deadLetter = awaitDeadLetter(invalid);
        assertEquals(KEY, deadLetter.key());
        assertEquals(InvalidRecordException.class.getName(), header(deadLetter, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN));
        assertEquals(properties.topic(), header(deadLetter, KafkaHeaders.DLT_ORIGINAL_TOPIC));
        assertEquals(0, rowsFor(recordId));
    }

    @Test
    void sendsMalformedMessageToDeadLetterTopicAndKeepsConsuming() {
        String malformed = "{not json " + UUID.randomUUID();
        String next = uniqueId();

        publish(malformed);
        publish(record(next, "src-1"));

        awaitDeadLetter(malformed);
        awaitStored(next);
    }
}
