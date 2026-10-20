package com.billogram.dataprocessing.messaging;

import com.billogram.dataprocessing.outbox.OutboxRepository;
import com.billogram.dataprocessing.domain.DataRecord;
import com.billogram.dataprocessing.domain.RecordAlertEvent;
import com.billogram.dataprocessing.domain.RecordType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxAlertPublisherTest {

    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final OutboxAlertPublisher publisher = new OutboxAlertPublisher(outbox, JsonMapper.builder().build(),
            new AlertProperties("record-alerts", Map.of()));

    @Test
    void storesAlertAsJsonKeyedByDestination() {
        DataRecord record = new DataRecord("r1", OffsetDateTime.parse("2026-10-20T12:00:00Z"), "src-1", "dst-1",
                RecordType.NEGATIVE, new BigDecimal("12500.75"), "EUR", "ref-1");

        publisher.publish(new RecordAlertEvent(record, new BigDecimal("10000")));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outbox).add(eq("record-alerts"), eq("dst-1"), payload.capture());
        JsonNode json = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build()
                .readTree(payload.getValue());
        assertEquals("r1", json.get("record").get("recordId").asString());
        assertEquals("negative", json.get("record").get("type").asString());
        assertEquals(new BigDecimal("12500.75"), json.get("record").get("value").decimalValue());
        assertEquals("EUR", json.get("record").get("unit").asString());
        assertEquals(0, new BigDecimal("10000").compareTo(json.get("threshold").decimalValue()));
    }
}
