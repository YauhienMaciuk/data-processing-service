package com.billogram.dataprocessing.api;

import com.billogram.dataprocessing.domain.RecordType;
import com.billogram.dataprocessing.domain.TestRecords;
import com.billogram.dataprocessing.persistence.RecordRepository;
import com.billogram.dataprocessing.ApiClient;
import com.billogram.dataprocessing.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.List;

import static com.billogram.dataprocessing.domain.TestRecords.record;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests of {@code GET /records}: query parameter binding, grouping, paging and the JSON response.
 * Filter and cursor semantics are covered in detail by {@link com.billogram.dataprocessing.persistence.RecordRepositoryIT},
 * grouping by {@code RecordServiceTest}.
 */
@IntegrationTest
class RecordQueryIT {

    private final JsonMapper json = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordRepository repository;

    @Autowired
    private RecordProperties properties;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        jdbc.update("DELETE FROM records");
        List.of(record("r1", at("08:00"), "dst-a", RecordType.POSITIVE, "100.50", "EUR"),
                record("r2", at("09:00"), "dst-a", RecordType.NEGATIVE, "30.25", "EUR"),
                record("r3", at("10:00"), "dst-a", RecordType.POSITIVE, "2", "kg"),
                record("r4", at("11:00"), "dst-b", RecordType.NEGATIVE, "10", "EUR"))
                .forEach(repository::insertIfAbsent);
    }

    private static OffsetDateTime at(String time) {
        return OffsetDateTime.parse("2026-10-20T" + time + ":00Z");
    }

    private JsonNode page(String query) {
        HttpResponse<String> response = api.get("/records" + query);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private JsonNode groups(String query) {
        return page(query).get("groups");
    }

    private static List<String> recordIds(JsonNode group) {
        return group.get("records").valueStream().map(r -> r.get("recordId").asString()).toList();
    }

    private static void assertTotal(String expected, JsonNode group, String unit) {
        assertEquals(0, new BigDecimal(expected).compareTo(group.get("totals").get(unit).decimalValue()),
                () -> "total " + unit + " was " + group.get("totals"));
    }

    @Test
    void returnsRecordsGroupedByDestinationWithSignedTotalsPerUnit() {
        JsonNode groups = groups("");

        assertEquals(2, groups.size());

        JsonNode a = groups.get(0);
        assertEquals("dst-a", a.get("destinationId").asString());
        assertEquals(List.of("r1", "r2", "r3"), recordIds(a));
        assertTotal("70.25", a, "EUR");   // 100.50 - 30.25
        assertTotal("2", a, "kg");

        JsonNode b = groups.get(1);
        assertEquals("dst-b", b.get("destinationId").asString());
        assertEquals(List.of("r4"), recordIds(b));
        assertTotal("-10", b, "EUR");
    }

    @Test
    void bindsAllQueryParametersIncludingUrlEncodedOffsets() {
        // 08:30Z .. 12:30+02:00 (= 10:30Z) covers r2 and r3; only r3 is positive
        JsonNode groups = groups("?type=positive&startTime=2026-10-20T08:30:00Z&endTime=2026-10-20T12:30:00%2B02:00");

        assertEquals(1, groups.size());
        assertEquals(List.of("r3"), recordIds(groups.get(0)));
    }

    @Test
    void returnsEmptyGroupsWhenNothingMatches() {
        assertEquals(0, groups("?startTime=2026-10-21T00:00:00Z").size());
    }

    @Test
    void rejectsUnconvertibleParametersWithFieldErrors() {
        HttpResponse<String> response = api.get("/records?type=neutral&startTime=yesterday");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"type\":\"invalid value\""), response.body());
        assertTrue(response.body().contains("\"startTime\":\"invalid value\""), response.body());
    }

    @Test
    void rejectsEndTimeNotAfterStartTime() {
        HttpResponse<String> response = api.get("/records?startTime=2026-10-20T10:00:00Z&endTime=2026-10-20T09:00:00Z");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("endTime must be after startTime"), response.body());
    }

    // --- paging

    @Test
    void returnsEverythingOnOnePageWhenItFitsTheDefaultLimit() {
        JsonNode page = page("");

        assertEquals(2, page.get("groups").size());
        assertTrue(page.get("nextCursor").isNull());
    }

    @Test
    void pagesThroughResultsWithNextCursorKeepingTotalsOfAllMatchingRecords() {
        JsonNode first = page("?limit=2");

        JsonNode firstGroups = first.get("groups");
        assertEquals(1, firstGroups.size());
        assertEquals(List.of("r1", "r2"), recordIds(firstGroups.get(0)));
        assertTotal("2", firstGroups.get(0), "kg");   // r3 is on the next page but counts towards the total

        JsonNode second = page("?limit=2&cursor=" + first.get("nextCursor").asString());

        JsonNode secondGroups = second.get("groups");
        assertEquals("dst-a", secondGroups.get(0).get("destinationId").asString());
        assertEquals(List.of("r3"), recordIds(secondGroups.get(0)));
        assertTotal("70.25", secondGroups.get(0), "EUR");
        assertEquals("dst-b", secondGroups.get(1).get("destinationId").asString());
        assertEquals(List.of("r4"), recordIds(secondGroups.get(1)));
        assertTrue(second.get("nextCursor").isNull());
    }

    @Test
    void rejectsLimitThatIsNotANumberWithFieldError() {
        HttpResponse<String> response = api.get("/records?limit=many");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"limit\":\"invalid value\""), response.body());
    }

    @Test
    void acceptsLimitFromOneToConfiguredMaximum() {
        int maxLimit = properties.query().maxLimit();

        assertEquals(400, api.get("/records?limit=0").statusCode());
        assertEquals(200, api.get("/records?limit=1").statusCode());
        assertEquals(200, api.get("/records?limit=" + maxLimit).statusCode());
        assertEquals(400, api.get("/records?limit=" + (maxLimit + 1)).statusCode());
    }

    @Test
    void rejectsCursorNotIssuedByTheEndpoint() {
        HttpResponse<String> response = api.get("/records?cursor=not-a-cursor");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("cursor must be a nextCursor returned by this endpoint"), response.body());
    }
}
