package com.billogram.dataprocessing.service;

import com.billogram.dataprocessing.domain.RecordAlertEvent;

/**
 * Publishes alerts to the alerting service for records whose value exceeds a threshold. Called inside the processing
 * transaction, so implementations must take part in it (e.g. write to an outbox table) rather than send directly.
 */
public interface AlertPublisher {

    void publish(RecordAlertEvent event);
}
