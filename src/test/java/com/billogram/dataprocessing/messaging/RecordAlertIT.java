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
import org.springframework.kafka.core.ConsumerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * End-to-end tests of alerts: processed record → threshold check → outbox → relay → alerts topic.
 * Values are derived from the configured EUR threshold, so the tests follow configuration changes.
 */
@IntegrationTest
class RecordAlertIT {

    private final JsonMapper json = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private AlertProperties alertProperties;

    private ApiClient api;
    private KafkaTopicReader alerts;
    private BigDecimal eurThreshold;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        alerts = new KafkaTopicReader(consumerFactory, alertProperties.topic());
        eurThreshold = alertProperties.thresholds().get("EUR");
    }

    @AfterEach
    void tearDown() {
        alerts.close();
    }

    private static String uniqueId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private String post(String type, BigDecimal value, String unit) {
        String recordId = uniqueId("alert");
        String body = """
                {"recordId": "%s", "time": "2026-10-20T10:00:00Z", "sourceId": "src-1", "destinationId": "dst-alerts",
                 "type": "%s", "value": %s, "unit": "%s"}""".formatted(recordId, type, value.toPlainString(), unit);
        assertEquals(201, api.post("/records", body).statusCode());
        return recordId;
    }

    private boolean isAlertFor(ConsumerRecord<String, String> message, String recordId) {
        return recordId.equals(json.readTree(message.value()).get("record").get("recordId").asString());
    }

    private boolean hasAlert(String recordId) {
        return !alerts.poll(message -> isAlertFor(message, recordId)).isEmpty();
    }

    @Test
    void alertsForValueAboveThresholdRegardlessOfType() {
        String positive = post("positive", eurThreshold.add(new BigDecimal("0.01")), "EUR");
        String negative = post("negative", eurThreshold.multiply(BigDecimal.TWO), "EUR");

        ConsumerRecord<String, String> message = alerts.awaitFirst(m -> isAlertFor(m, positive));
        JsonNode alert = json.readTree(message.value());
        assertEquals("dst-alerts", message.key());
        assertEquals(0, eurThreshold.compareTo(alert.get("threshold").decimalValue()));
        assertEquals("EUR", alert.get("record").get("unit").asString());

        alerts.awaitFirst(m -> isAlertFor(m, negative));
    }

    @Test
    void doesNotAlertAtOrBelowThresholdOrForUnitsWithoutOne() {
        String atThreshold = post("positive", eurThreshold, "EUR");
        String belowThreshold = post("positive", eurThreshold.subtract(BigDecimal.ONE), "EUR");
        String unconfiguredUnit = post("positive", eurThreshold.multiply(BigDecimal.TEN), "parsecs");
        String marker = post("positive", eurThreshold.add(BigDecimal.ONE), "EUR");

        // Alerts are relayed in the order they were stored, so once the marker's alert arrives all earlier ones have
        alerts.awaitFirst(m -> isAlertFor(m, marker));
        assertFalse(hasAlert(atThreshold), "value equal to the threshold must not alert");
        assertFalse(hasAlert(belowThreshold), "value below the threshold must not alert");
        assertFalse(hasAlert(unconfiguredUnit), "unit without a threshold must not alert");
    }
}
