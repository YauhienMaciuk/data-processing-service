package com.billogram.dataprocessing.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import com.billogram.dataprocessing.ApiClient;
import com.billogram.dataprocessing.IntegrationTest;
import com.billogram.dataprocessing.KafkaTopicReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests of notification events: a record processed via any channel → outbox → relay → Kafka topic.
 * Each test uses its own destination id, so summaries aren't affected by records from other tests.
 */
@IntegrationTest
class RecordNotificationIT {

    private final JsonMapper json = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private NotificationProperties notificationProperties;

    @Autowired
    private RecordKafkaProperties recordKafkaProperties;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient api;
    private KafkaTopicReader notifications;
    private String destination;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        notifications = new KafkaTopicReader(consumerFactory, notificationProperties.topic());
        destination = "dst-" + UUID.randomUUID();
        jdbc.update("DELETE FROM records");
    }

    @AfterEach
    void tearDown() {
        notifications.close();
    }

    private String record(String recordId, String time, String type, String value, String unit, String reference) {
        String referenceJson = reference == null ? "null" : "\"" + reference + "\"";
        return """
                {"recordId": "%s", "time": "2026-10-20T%s:00Z", "sourceId": "src-1", "destinationId": "%s",
                 "type": "%s", "value": %s, "unit": "%s", "reference": %s}"""
                .formatted(recordId, time, destination, type, value, unit, referenceJson);
    }

    private void post(String recordJson) {
        assertEquals(201, api.post("/records", recordJson).statusCode());
    }

    private boolean isEventFor(ConsumerRecord<String, String> message, String recordId) {
        return recordId.equals(json.readTree(message.value()).get("record").get("recordId").asString());
    }

    private ConsumerRecord<String, String> awaitEventMessage(String recordId) {
        return notifications.awaitFirst(message -> isEventFor(message, recordId));
    }

    private JsonNode awaitEvent(String recordId) {
        return json.readTree(awaitEventMessage(recordId).value());
    }

    private int eventsFor(String recordId) {
        return notifications.poll(message -> isEventFor(message, recordId)).size();
    }

    private static void assertSummary(JsonNode event, long count, String firstTime, String lastTime) {
        JsonNode previous = event.get("previousRecords");
        assertEquals(count, previous.get("count").asLong(), previous::toString);
        assertEquals(firstTime, previous.get("firstTime").isNull() ? null : previous.get("firstTime").asString());
        assertEquals(lastTime, previous.get("lastTime").isNull() ? null : previous.get("lastTime").asString());
    }

    private static void assertTotal(JsonNode event, String unit, String expected) {
        BigDecimal actual = event.get("previousRecords").get("totals").get(unit).decimalValue();
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> unit + " total was " + actual);
    }

    @Test
    void eventSummarizesPreviousRecordsWithSameDestinationAndReference() {
        post(record("n1", "09:00", "positive", "100.50", "EUR", "ref-1"));
        post(record("n2", "10:00", "negative", "30.25", "EUR", "ref-1"));
        post(record("n3", "11:00", "positive", "2", "kg", "ref-1"));
        post(record("n4", "12:00", "positive", "5", "EUR", "ref-2"));

        ConsumerRecord<String, String> first = awaitEventMessage("n1");
        assertEquals(destination, first.key());
        assertSummary(json.readTree(first.value()), 0, null, null);

        JsonNode third = awaitEvent("n3");
        assertEquals("n3", third.get("record").get("recordId").asString());
        assertEquals("kg", third.get("record").get("unit").asString());
        assertSummary(third, 2, "2026-10-20T09:00:00Z", "2026-10-20T10:00:00Z");
        assertTotal(third, "EUR", "70.25");   // 100.50 - 30.25

        assertSummary(awaitEvent("n4"), 0, null, null);   // different reference
    }

    @Test
    void recordsWithoutReferenceAreSummarizedTogether() {
        post(record("a1", "09:00", "positive", "5", "EUR", null));
        post(record("a2", "10:00", "positive", "7", "EUR", null));

        JsonNode second = awaitEvent("a2");
        assertSummary(second, 1, "2026-10-20T09:00:00Z", "2026-10-20T09:00:00Z");
        assertTotal(second, "EUR", "5");
    }

    @Test
    void recordConsumedFromKafkaProducesEvent() {
        kafkaTemplate.send(recordKafkaProperties.topic(), "it", record("k1", "09:00", "positive", "1", "EUR", "ref-1")).join();

        assertSummary(awaitEvent("k1"), 0, null, null);
    }

    @Test
    void onlyCreatedRecordsProduceEvents() {
        post(record("c1", "09:00", "positive", "1", "EUR", "ref-1"));
        assertEquals(409, api.post("/records", record("c1", "09:00", "positive", "1", "EUR", "ref-1")).statusCode());
        assertEquals(400, api.post("/records", "{\"recordId\": \"c2\"}").statusCode());
        post(record("marker", "11:00", "positive", "1", "EUR", "ref-1"));

        // Events are relayed in the order they were stored, so once the marker's event arrives all earlier ones have
        JsonNode marker = awaitEvent("marker");
        assertSummary(marker, 1, "2026-10-20T09:00:00Z", "2026-10-20T09:00:00Z");
        assertEquals(1, eventsFor("c1"));
        assertTrue(notifications.poll(message -> isEventFor(message, "c2")).isEmpty());
    }

    @Test
    void concurrentRecordsForTheSameDestinationAndReferenceEachSeeAllEarlierOnes() {
        int records = 20;
        List<CompletableFuture<HttpResponse<String>>> responses = IntStream.range(0, records)
                .mapToObj(i -> api.postAsync("/records", record("p" + i, "09:00", "positive", "1", "EUR", "ref-1")))
                .toList();
        responses.forEach(response -> assertEquals(201, response.join().statusCode()));

        // One after another, the records saw 0, 1, ..., 19 earlier ones; summaries built at the same time repeat counts
        List<Long> previousCounts = await().atMost(KafkaTopicReader.TIMEOUT)
                .until(() -> notifications.poll(message -> destination.equals(message.key())),
                        events -> events.size() >= records)
                .stream()
                .map(event -> json.readTree(event.value()).get("previousRecords").get("count").asLong())
                .sorted()
                .toList();
        assertEquals(LongStream.range(0, records).boxed().toList(), previousCounts);
    }
}
