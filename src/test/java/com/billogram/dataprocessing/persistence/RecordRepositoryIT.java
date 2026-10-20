package com.billogram.dataprocessing.persistence;

import com.billogram.dataprocessing.IntegrationTest;
import com.billogram.dataprocessing.domain.DataRecord;
import com.billogram.dataprocessing.domain.RecordCursor;
import com.billogram.dataprocessing.domain.RecordFilter;
import com.billogram.dataprocessing.domain.RecordQuery;
import com.billogram.dataprocessing.domain.RecordType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.billogram.dataprocessing.domain.TestRecords.record;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class RecordRepositoryIT {

    private static final RecordFilter NO_FILTER = new RecordFilter(null, null, null);
    private static final int ALL = 100;

    @Autowired
    private RecordRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM records");
    }

    private static OffsetDateTime at(String time) {
        return OffsetDateTime.parse("2026-10-20T" + time + ":00Z");
    }

    private static List<String> recordIds(List<DataRecord> records) {
        return records.stream().map(DataRecord::recordId).toList();
    }

    private void insertAll(DataRecord... records) {
        List.of(records).forEach(repository::insertIfAbsent);
    }

    private void insert(DataRecord record) {
        assertTrue(repository.insertIfAbsent(record));
    }

    /** All records matching the filter, in query order. */
    private List<DataRecord> find(RecordFilter filter) {
        return repository.find(new RecordQuery(filter, ALL, null));
    }

    /** Follows the cursor from page to page and returns the record ids in the order they came. */
    private List<String> pageThrough(int limit) {
        List<String> recordIds = new ArrayList<>();
        RecordCursor after = null;
        List<DataRecord> page;
        do {
            page = repository.find(new RecordQuery(NO_FILTER, limit, after));
            recordIds.addAll(recordIds(page));
            after = page.isEmpty() ? null : RecordCursor.after(page.getLast());
        } while (page.size() == limit);
        return recordIds;
    }

    /** Two destinations with ties on time (r1/r2, r4/r5), which pages must break by record id. */
    private void insertRecordsWithTimeTies() {
        insertAll(
                record("r1", at("09:00"), "dst-a", RecordType.POSITIVE, "5", "EUR"),
                record("r2", at("09:00"), "dst-a", RecordType.NEGATIVE, "5", "EUR"),
                record("r3", at("08:00"), "dst-a", RecordType.POSITIVE, "7", "EUR"),
                record("r4", at("10:00"), "dst-b", RecordType.POSITIVE, "1", "EUR"),
                record("r5", at("10:00"), "dst-b", RecordType.POSITIVE, "3", "EUR"));
    }

    private static void assertTotals(Map<String, String> expected, Map<String, BigDecimal> totals) {
        assertEquals(expected.keySet(), totals.keySet());
        expected.forEach((unit, total) -> assertEquals(0, new BigDecimal(total).compareTo(totals.get(unit)),
                () -> "total " + unit + " was " + totals));
    }

    // --- insertIfAbsent

    @Test
    void insertIfAbsentStoresNewRecordsAndLeavesDuplicatesUnchanged() {
        assertTrue(repository.insertIfAbsent(record("r1")));
        assertTrue(repository.insertIfAbsent(record("r2")));

        assertFalse(repository.insertIfAbsent(record("r1", "dst-2", RecordType.NEGATIVE, "5", "kg")));
        assertEquals(List.of(record("r1"), record("r2")), find(NO_FILTER));
    }

    // --- find

    @Test
    void findMapsAllColumnsAndNormalizesTimeToUtc() {
        DataRecord stored = new DataRecord("r1", OffsetDateTime.parse("2026-10-20T12:00:00+02:00"), "src-1", "dst-1",
                RecordType.NEGATIVE, new BigDecimal("12345.678901234567890"), "EUR", "ref-1");
        repository.insertIfAbsent(stored);

        DataRecord found = find(NO_FILTER).getFirst();

        assertEquals(new DataRecord("r1", at("10:00"), "src-1", "dst-1",
                RecordType.NEGATIVE, new BigDecimal("12345.678901234567890"), "EUR", "ref-1"), found);
    }

    @Test
    void findOrdersByDestinationThenTimeThenRecordId() {
        insertAll(
                record("r4", at("09:00"), "dst-b", RecordType.POSITIVE, "1", "EUR"),
                record("r3", at("11:00"), "dst-a", RecordType.POSITIVE, "1", "EUR"),
                record("r2", at("10:00"), "dst-a", RecordType.POSITIVE, "1", "EUR"),
                record("r1", at("10:00"), "dst-a", RecordType.POSITIVE, "1", "EUR"));

        assertEquals(List.of("r1", "r2", "r3", "r4"), recordIds(find(NO_FILTER)));
    }

    @Test
    void findAppliesEachFilter() {
        insertAll(
                record("r1", at("08:00"), "dst-1", RecordType.POSITIVE, "1", "EUR"),
                record("r2", at("09:00"), "dst-1", RecordType.NEGATIVE, "1", "EUR"),
                record("r3", at("10:00"), "dst-1", RecordType.POSITIVE, "1", "EUR"));

        assertEquals(List.of("r2", "r3"), recordIds(find(new RecordFilter(at("09:00"), null, null))));
        assertEquals(List.of("r1"), recordIds(find(new RecordFilter(null, at("09:00"), null))));
        assertEquals(List.of("r1", "r3"), recordIds(find(new RecordFilter(null, null, RecordType.POSITIVE))));
        assertEquals(List.of("r3"),
                recordIds(find(new RecordFilter(at("08:30"), at("10:30"), RecordType.POSITIVE))));
    }

    @Test
    void findTreatsStartAsInclusiveAndEndAsExclusive() {
        insert(record("r1", at("09:00"), "dst-1", RecordType.POSITIVE, "1", "EUR"));

        assertEquals(List.of("r1"), recordIds(find(new RecordFilter(at("09:00"), at("09:01"), null))));
        assertEquals(List.of(), recordIds(find(new RecordFilter(at("08:59"), at("09:00"), null))));
    }

    @Test
    void findReturnsAtMostTheLimit() {
        insertRecordsWithTimeTies();

        assertEquals(List.of("r3", "r1"), recordIds(repository.find(new RecordQuery(NO_FILTER, 2, null))));
    }

    @Test
    void pagesByTimeWithinEachDestinationBreakingTiesByRecordId() {
        insertRecordsWithTimeTies();

        assertEquals(List.of("r3", "r1", "r2", "r4", "r5"), pageThrough(2));
    }

    @Test
    void appliesFilterTogetherWithCursor() {
        insertRecordsWithTimeTies();
        RecordCursor afterR3 = RecordCursor.after(record("r3", at("08:00"), "dst-a", RecordType.POSITIVE, "7", "EUR"));

        List<DataRecord> positiveAfterR3 =
                repository.find(new RecordQuery(new RecordFilter(null, null, RecordType.POSITIVE), ALL, afterR3));

        assertEquals(List.of("r1", "r4", "r5"), recordIds(positiveAfterR3));
    }

    // --- totalsByDestination

    @Test
    void totalsSignedValuesPerUnitOfTheGivenDestinationsOnly() {
        insertAll(
                record("a1", at("08:00"), "dst-a", RecordType.POSITIVE, "100.50", "EUR"),
                record("a2", at("09:00"), "dst-a", RecordType.NEGATIVE, "30.25", "EUR"),
                record("a3", at("10:00"), "dst-a", RecordType.POSITIVE, "2", "kg"),
                record("b1", at("11:00"), "dst-b", RecordType.NEGATIVE, "10", "EUR"),
                record("c1", at("12:00"), "dst-c", RecordType.POSITIVE, "5", "EUR"));

        Map<String, Map<String, BigDecimal>> totals = repository.totalsByDestination(NO_FILTER, Set.of("dst-a", "dst-b"));

        assertEquals(Set.of("dst-a", "dst-b"), totals.keySet());
        assertTotals(Map.of("EUR", "70.25", "kg", "2"), totals.get("dst-a"));   // 100.50 - 30.25
        assertTotals(Map.of("EUR", "-10"), totals.get("dst-b"));
    }

    @Test
    void totalsIncludeOnlyRecordsMatchingTheFilter() {
        insertAll(
                record("a1", at("08:00"), "dst-a", RecordType.POSITIVE, "100.50", "EUR"),
                record("a2", at("09:00"), "dst-a", RecordType.NEGATIVE, "30.25", "EUR"),
                record("a3", at("10:00"), "dst-a", RecordType.POSITIVE, "2", "kg"));

        Map<String, Map<String, BigDecimal>> totals =
                repository.totalsByDestination(new RecordFilter(at("09:00"), null, RecordType.POSITIVE), Set.of("dst-a"));

        assertTotals(Map.of("kg", "2"), totals.get("dst-a"));
    }

    @Test
    void totalsOfNoDestinationsAreEmpty() {
        insert(record("r1"));

        assertEquals(Map.of(), repository.totalsByDestination(NO_FILTER, Set.of()));
    }
}
