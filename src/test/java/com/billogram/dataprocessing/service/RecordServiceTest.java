package com.billogram.dataprocessing.service;

import com.billogram.dataprocessing.domain.DataRecord;
import com.billogram.dataprocessing.domain.DuplicateRecordException;
import com.billogram.dataprocessing.domain.RecordCursor;
import com.billogram.dataprocessing.domain.RecordFilter;
import com.billogram.dataprocessing.domain.RecordGroup;
import com.billogram.dataprocessing.domain.RecordAlertEvent;
import com.billogram.dataprocessing.domain.RecordPage;
import com.billogram.dataprocessing.domain.RecordProcessedEvent;
import com.billogram.dataprocessing.domain.RecordQuery;
import com.billogram.dataprocessing.domain.RecordSummary;
import com.billogram.dataprocessing.domain.RecordType;
import com.billogram.dataprocessing.domain.TestRecords;
import com.billogram.dataprocessing.domain.ValueThresholds;
import com.billogram.dataprocessing.persistence.RecordRepository;
import com.billogram.dataprocessing.persistence.RecordSummaryRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.billogram.dataprocessing.domain.TestRecords.record;
import static com.billogram.dataprocessing.domain.TestRecords.withReference;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RecordServiceTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final RecordFilter NO_FILTER = new RecordFilter(null, null, null);

    private static final RecordSummary PREVIOUS = new RecordSummary(2, Map.of("EUR", BigDecimal.TEN),
            TestRecords.TIME.minusHours(2), TestRecords.TIME.minusHours(1));

    private static final BigDecimal EUR_THRESHOLD = new BigDecimal("100");

    private final RecordRepository repository = mock(RecordRepository.class);
    private final RecordSummaryRepository summaries = mock(RecordSummaryRepository.class);
    private final NotificationPublisher notificationPublisher = mock(NotificationPublisher.class);
    private final AlertPublisher alertPublisher = mock(AlertPublisher.class);
    private final RecordService service = new RecordService(repository, summaries, VALIDATOR_FACTORY.getValidator(),
            notificationPublisher, alertPublisher, new ValueThresholds(Map.of("EUR", EUR_THRESHOLD)));

    @AfterAll
    static void closeFactory() {
        VALIDATOR_FACTORY.close();
    }

    private void givenStored(DataRecord record) {
        when(repository.insertIfAbsent(record)).thenReturn(true);
        when(summaries.lock(record.destinationId(), record.reference())).thenReturn(PREVIOUS);
    }

    private void givenDuplicate(DataRecord record) {
        when(repository.insertIfAbsent(record)).thenReturn(false);
    }

    private static DataRecord aboveThreshold(String recordId) {
        return record(recordId, "dst-1", RecordType.POSITIVE, "150", "EUR");
    }

    // --- process

    @Test
    void processStoresRecordAddsItToTheRunningSummaryAndPublishesTheSummaryOfEarlierRecords() {
        DataRecord record = withReference(record("r1"), "ref-1");
        givenStored(record);

        service.process(record);

        InOrder order = inOrder(repository, summaries);
        order.verify(repository).insertIfAbsent(record);
        order.verify(summaries).lock("dst-1", "ref-1");
        order.verify(summaries).update("dst-1", "ref-1", PREVIOUS.including(record));
        verify(notificationPublisher).publish(new RecordProcessedEvent(record, PREVIOUS));
        verifyNoInteractions(alertPublisher);   // value 1 EUR is below the threshold
    }

    @Test
    void processPublishesAlertWhenValueExceedsThreshold() {
        DataRecord record = aboveThreshold("r1");
        givenStored(record);

        service.process(record);

        verify(notificationPublisher).publish(new RecordProcessedEvent(record, PREVIOUS));
        verify(alertPublisher).publish(new RecordAlertEvent(record, EUR_THRESHOLD));
    }

    @Test
    void processRejectsAlreadyProcessedRecordWithoutSummarizingPublishingOrAlerting() {
        givenDuplicate(aboveThreshold("r1"));

        DuplicateRecordException exception =
                assertThrows(DuplicateRecordException.class, () -> service.process(aboveThreshold("r1")));

        assertEquals("r1", exception.getRecordId());
        verifyNoInteractions(summaries, notificationPublisher, alertPublisher);
    }

    // --- validate

    @Test
    void validateReturnsNoErrorsForValidRecord() {
        assertEquals(Map.of(), service.validate(record("r1")));
    }

    @Test
    void validateReturnsOneMessagePerInvalidFieldSortedByName() {
        DataRecord record = new DataRecord("r1", null, "x".repeat(256), "dst-1", null, BigDecimal.ONE, "EUR", null);

        assertEquals(List.of("sourceId", "time", "type"), List.copyOf(service.validate(record).keySet()));
    }

    // --- findGroupedByDestination

    private static RecordQuery firstPage(int limit) {
        return new RecordQuery(NO_FILTER, limit, null);
    }

    @Test
    void groupsPageByDestinationInResultOrderWithTotalsOfAllMatchingRecords() {
        DataRecord a1 = record("a1", "dst-a", RecordType.POSITIVE, "100.50", "EUR");
        DataRecord a2 = record("a2", "dst-a", RecordType.NEGATIVE, "30.25", "EUR");
        DataRecord b1 = record("b1", "dst-b", RecordType.NEGATIVE, "10", "EUR");
        // Totals cover matching records on other pages too, e.g. dst-a's kg records
        Map<String, BigDecimal> totalsA = Map.of("EUR", new BigDecimal("70.25"), "kg", new BigDecimal("2"));
        Map<String, BigDecimal> totalsB = Map.of("EUR", new BigDecimal("-10"));
        when(repository.find(firstPage(11))).thenReturn(List.of(a1, a2, b1));
        when(repository.totalsByDestination(NO_FILTER, Set.of("dst-a", "dst-b")))
                .thenReturn(Map.of("dst-a", totalsA, "dst-b", totalsB));

        RecordPage page = service.findGroupedByDestination(firstPage(10));

        assertEquals(new RecordPage(List.of(
                new RecordGroup("dst-a", totalsA, List.of(a1, a2)),
                new RecordGroup("dst-b", totalsB, List.of(b1))), null), page);
    }

    @Test
    void returnsCursorAfterLastRecordWhenMoreRecordsMatchThanTheLimit() {
        DataRecord r1 = record("r1");
        DataRecord r2 = record("r2");
        when(repository.find(firstPage(3))).thenReturn(List.of(r1, r2, record("r3")));   // limit + 1

        RecordPage page = service.findGroupedByDestination(firstPage(2));

        assertEquals(List.of(r1, r2), page.groups().getFirst().records());
        assertEquals(RecordCursor.after(r2), page.next());
    }

    @Test
    void passesQueryToRepositoryAndAsksTotalsForThePagesDestinations() {
        RecordFilter filter = new RecordFilter(TestRecords.TIME, null, RecordType.NEGATIVE);
        RecordQuery query = new RecordQuery(filter, 5, RecordCursor.after(record("r0")));
        when(repository.find(query.withLimit(6)))
                .thenReturn(List.of(record("r1"), record("r2", "dst-2", RecordType.NEGATIVE, "1", "EUR")));

        service.findGroupedByDestination(query);

        verify(repository).totalsByDestination(filter, Set.of("dst-1", "dst-2"));
    }

    @Test
    void returnsEmptyLastPageWhenNothingMatches() {
        when(repository.find(firstPage(11))).thenReturn(List.of());

        assertEquals(new RecordPage(List.of(), null), service.findGroupedByDestination(firstPage(10)));
    }
}
