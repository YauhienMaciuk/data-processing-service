package com.billogram.dataprocessing.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordKafkaPropertiesTest {

    @Test
    void bindsAllSettings() {
        Map<String, String> source = Map.of(
                "records.kafka.topic", "incoming",
                "records.kafka.dead-letter-topic", "incoming-dlt",
                "records.kafka.retry.interval", "500ms",
                "records.kafka.retry.max-retries", "5");

        RecordKafkaProperties properties = new Binder(new MapConfigurationPropertySource(source))
                .bind("records.kafka", RecordKafkaProperties.class)
                .get();

        assertEquals(new RecordKafkaProperties("incoming", "incoming-dlt",
                new RecordKafkaProperties.Retry(Duration.ofMillis(500), 5)), properties);
    }
}
