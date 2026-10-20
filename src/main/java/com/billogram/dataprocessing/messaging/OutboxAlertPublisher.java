package com.billogram.dataprocessing.messaging;

import com.billogram.dataprocessing.outbox.OutboxRepository;
import com.billogram.dataprocessing.domain.RecordAlertEvent;
import com.billogram.dataprocessing.service.AlertPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes alerts to the alerting service through the transactional outbox: the alert is stored as JSON in the
 * caller's transaction and sent to Kafka by {@link com.billogram.dataprocessing.outbox.OutboxRelay} after it commits.
 * Messages are keyed by destination id, like notifications.
 */
@Component
public class OutboxAlertPublisher implements AlertPublisher {

    private final OutboxRepository outbox;
    private final JsonMapper jsonMapper;
    private final AlertProperties properties;

    public OutboxAlertPublisher(OutboxRepository outbox, JsonMapper jsonMapper, AlertProperties properties) {
        this.outbox = outbox;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
    }

    @Override
    public void publish(RecordAlertEvent event) {
        outbox.add(properties.topic(), event.record().destinationId(), jsonMapper.writeValueAsString(event));
    }
}
