package com.billogram.dataprocessing.messaging;

import com.billogram.dataprocessing.outbox.OutboxRepository;
import com.billogram.dataprocessing.domain.DataRecord;
import com.billogram.dataprocessing.domain.RecordProcessedEvent;
import com.billogram.dataprocessing.domain.RecordSummary;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxNotificationPublisherTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final OutboxNotificationPublisher publisher =
            new OutboxNotificationPublisher(outbox, jsonMapper, new NotificationProperties("record-notifications"));

    private JsonNode publishedPayload(RecordProcessedEvent event, String expectedKey) {
        publisher.publish(event);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outbox).add(eq("record-notifications"), eq(expectedKey), payload.capture());
        return JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build()
                .readTree(payload.getValue());
    }

    @Test
    void storesNotificationAsJsonKeyedByDestination() {
        DataRecord record = new DataRecord("r1", OffsetDateTime.parse("2026-10-20T12:00:00Z"), "src-1", "dst-1",
                RecordType.NEGATIVE, new BigDecimal("12.50"), "EUR", "ref-1");
        RecordSummary previous = new RecordSummary(2, Map.of("EUR", new BigDecimal("70.25")),
                OffsetDateTime.parse("2026-10-20T08:00:00Z"), OffsetDateTime.parse("2026-10-20T11:00:00Z"));

        JsonNode json = publishedPayload(new RecordProcessedEvent(record, previous), "dst-1");

        JsonNode recordJson = json.get("record");
        assertEquals("r1", recordJson.get("recordId").asString());
        assertEquals("2026-10-20T12:00:00Z", recordJson.get("time").asString());
        assertEquals("negative", recordJson.get("type").asString());
        assertEquals(new BigDecimal("12.50"), recordJson.get("value").decimalValue());
        assertEquals("ref-1", recordJson.get("reference").asString());

        JsonNode previousJson = json.get("previousRecords");
        assertEquals(2, previousJson.get("count").asLong());
        assertEquals(new BigDecimal("70.25"), previousJson.get("totals").get("EUR").decimalValue());
        assertEquals("2026-10-20T08:00:00Z", previousJson.get("firstTime").asString());
        assertEquals("2026-10-20T11:00:00Z", previousJson.get("lastTime").asString());
    }

    @Test
    void storesEmptySummaryWithNullTimes() {
        DataRecord record = new DataRecord("r1", OffsetDateTime.parse("2026-10-20T12:00:00Z"), "src-1", "dst-2",
                RecordType.POSITIVE, BigDecimal.ONE, "EUR", null);

        JsonNode previousJson = publishedPayload(new RecordProcessedEvent(record, RecordSummary.empty()), "dst-2")
                .get("previousRecords");

        assertEquals(0, previousJson.get("count").asLong());
        assertEquals(0, previousJson.get("totals").size());
        assertTrue(previousJson.get("firstTime").isNull());
        assertTrue(previousJson.get("lastTime").isNull());
    }
}
