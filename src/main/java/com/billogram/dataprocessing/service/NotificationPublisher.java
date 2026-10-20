package com.billogram.dataprocessing.service;

import com.billogram.dataprocessing.domain.RecordProcessedEvent;

/**
 * Publishes a notification for each processed record to the Notification service. Called inside the processing
 * transaction, so implementations must take part in it (e.g. write to an outbox table) rather than send directly;
 * otherwise notifications could escape for rolled-back records.
 */
public interface NotificationPublisher {

    void publish(RecordProcessedEvent event);
}
