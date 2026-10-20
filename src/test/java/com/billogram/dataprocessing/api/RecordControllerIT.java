package com.billogram.dataprocessing.api;

import com.billogram.dataprocessing.ApiClient;
import com.billogram.dataprocessing.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end tests of {@code POST /records}: HTTP → JSON → validation → Postgres. */
@IntegrationTest
class RecordControllerIT {

    private static final String VALID_RECORD = """
            {
              "recordId": "rec-1",
              "time": "2026-10-20T10:00:00+02:00",
              "sourceId": "src-1",
              "destinationId": "dst-1",
              "type": "positive",
              "value": 12345.678901234567890,
              "unit": "EUR",
              "reference": "ref-1"
            }""";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        jdbc.update("DELETE FROM records");
    }

    private HttpResponse<String> post(String json) {
        return api.post("/records", json);
    }

    private int count() {
        return jdbc.queryForObject("SELECT count(*) FROM records", Integer.class);
    }

    @Test
    void storesValidRecord() {
        HttpResponse<String> response = post(VALID_RECORD);

        assertEquals(201, response.statusCode());
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM records WHERE record_id = 'rec-1'");
        assertEquals(Timestamp.from(Instant.parse("2026-10-20T08:00:00Z")), row.get("time"));
        assertEquals("src-1", row.get("source_id"));
        assertEquals("dst-1", row.get("destination_id"));
        assertEquals("positive", row.get("type"));
        assertEquals(new BigDecimal("12345.678901234567890"), row.get("value"));
        assertEquals("EUR", row.get("unit"));
        assertEquals("ref-1", row.get("reference"));
    }

    @Test
    void referenceIsOptional() {
        HttpResponse<String> response = post(VALID_RECORD.replace(",\n  \"reference\": \"ref-1\"", ""));

        assertEquals(201, response.statusCode());
        assertNull(jdbc.queryForObject("SELECT reference FROM records WHERE record_id = 'rec-1'", String.class));
    }

    @Test
    void duplicateRecordIdIsRejected() {
        assertEquals(201, post(VALID_RECORD).statusCode());

        HttpResponse<String> response = post(VALID_RECORD.replace("src-1", "src-2"));

        assertEquals(409, response.statusCode());
        assertTrue(response.body().contains("Record with recordId 'rec-1' has already been processed"), response.body());
        assertTrue(response.body().contains("\"recordId\":\"rec-1\""), response.body());
        assertEquals(1, count());
        assertEquals("src-1", jdbc.queryForObject("SELECT source_id FROM records", String.class));
    }

    @Test
    void concurrentDuplicatesAreProcessedOnce() {
        List<Integer> statuses = IntStream.range(0, 20)
                .mapToObj(i -> api.postAsync("/records", VALID_RECORD))
                .toList()
                .stream()
                .map(CompletableFuture::join)
                .map(HttpResponse::statusCode)
                .toList();

        assertEquals(1, statuses.stream().filter(status -> status == 201).count(), statuses::toString);
        assertEquals(19, statuses.stream().filter(status -> status == 409).count(), statuses::toString);
        assertEquals(1, count());
    }

    @Test
    void missingFieldsAreRejectedWithFieldErrors() {
        HttpResponse<String> response = post("""
                {"recordId": "rec-1", "type": "positive", "value": 1, "unit": "EUR"}""");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"time\""), response.body());
        assertTrue(response.body().contains("\"sourceId\""), response.body());
        assertTrue(response.body().contains("\"destinationId\""), response.body());
        assertEquals(0, count());
    }

    @Test
    void unknownTypeIsRejected() {
        assertEquals(400, post(VALID_RECORD.replace("positive", "neutral")).statusCode());
    }

    @Test
    void malformedJsonIsRejected() {
        assertEquals(400, post("{not json").statusCode());
    }
}
