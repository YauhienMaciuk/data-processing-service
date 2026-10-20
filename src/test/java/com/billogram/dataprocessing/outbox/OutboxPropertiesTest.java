package com.billogram.dataprocessing.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutboxPropertiesTest {

    @Test
    void bindsAllSettings() {
        Map<String, String> source = Map.of(
                "outbox.relay.interval", "250ms",
                "outbox.relay.batch-size", "20",
                "outbox.relay.send-timeout", "5s");

        OutboxProperties properties = new Binder(new MapConfigurationPropertySource(source))
                .bind("outbox.relay", OutboxProperties.class)
                .get();

        assertEquals(new OutboxProperties(Duration.ofMillis(250), 20, Duration.ofSeconds(5)), properties);
    }
}
