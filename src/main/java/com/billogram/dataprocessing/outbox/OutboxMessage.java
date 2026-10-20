package com.billogram.dataprocessing.outbox;

public record OutboxMessage(long id, String topic, String key, String payload) {
}
