package com.billogram.dataprocessing.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationConfigurationTest {

    @Test
    void declaresConfiguredTopic() {
        NotificationProperties properties = new NotificationProperties("record-notifications");

        assertEquals("record-notifications", new NotificationConfiguration().notificationsTopic(properties).name());
    }

    @Test
    void bindsTopicFromProperties() {
        NotificationProperties properties = new Binder(new MapConfigurationPropertySource(
                Map.of("records.notifications.topic", "notify")))
                .bind("records.notifications", NotificationProperties.class)
                .get();

        assertEquals(new NotificationProperties("notify"), properties);
    }
}
