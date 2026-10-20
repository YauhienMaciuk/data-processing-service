package com.billogram.dataprocessing.messaging;

import com.billogram.dataprocessing.outbox.OutboxRepository;
import com.billogram.dataprocessing.domain.RecordProcessedEvent;
import com.billogram.dataprocessing.service.NotificationPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes notifications to the Notification service through the transactional outbox: each one is stored as JSON
 * in the caller's transaction and sent to Kafka by {@link com.billogram.dataprocessing.outbox.OutboxRelay} after it commits.
 * Messages are keyed by destination id, so notifications for one destination are delivered in processing order.
 */
@Component
public class OutboxNotificationPublisher implements NotificationPublisher {

    private final OutboxRepository outbox;
    private final JsonMapper jsonMapper;
    private final NotificationProperties properties;

    public OutboxNotificationPublisher(OutboxRepository outbox, JsonMapper jsonMapper, NotificationProperties properties) {
        this.outbox = outbox;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
    }

    @Override
    public void publish(RecordProcessedEvent event) {
        outbox.add(properties.topic(), event.record().destinationId(), jsonMapper.writeValueAsString(event));
    }
}
