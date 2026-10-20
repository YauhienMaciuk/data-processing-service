package com.billogram.dataprocessing.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class RecordKafkaConfigurationTest {

    private final RecordKafkaConfiguration configuration = new RecordKafkaConfiguration();
    private final RecordKafkaProperties properties = new RecordKafkaProperties("records", "records-dlt",
            new RecordKafkaProperties.Retry(Duration.ofSeconds(1), 3));

    @Test
    void declaresConfiguredTopics() {
        assertEquals("records", configuration.recordsTopic(properties).name());
        assertEquals("records-dlt", configuration.recordsDeadLetterTopic(properties).name());
    }

    @Test
    void sendsFailedMessagesToDeadLetterTopicOnAnyPartition() {
        ConsumerRecord<String, String> failed = new ConsumerRecord<>("records", 2, 42L, "key", "value");

        TopicPartition destination = RecordKafkaConfiguration.deadLetterDestination("records-dlt")
                .apply(failed, new IllegalStateException("boom"));

        assertEquals(new TopicPartition("records-dlt", -1), destination);
    }

    @Test
    void createsErrorHandler() {
        assertNotNull(configuration.recordErrorHandler(mock(KafkaTemplate.class), properties));
    }
}
