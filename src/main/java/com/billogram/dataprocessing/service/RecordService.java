package com.billogram.dataprocessing.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
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
import com.billogram.dataprocessing.domain.ValueThresholds;
import com.billogram.dataprocessing.persistence.RecordRepository;
import com.billogram.dataprocessing.persistence.RecordSummaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Processes incoming records regardless of the channel they arrived on (HTTP or Kafka).
 * Every successfully processed record publishes a {@link RecordProcessedEvent} in the same transaction, with a summary
 * of the earlier records for its destination and reference, plus a {@link RecordAlertEvent} if its value exceeds
 * the threshold for its unit.
 */
@Service
public class RecordService {

    private final RecordRepository repository;
    private final RecordSummaryRepository summaries;
    private final Validator validator;
    private final NotificationPublisher notificationPublisher;
    private final AlertPublisher alertPublisher;
    private final ValueThresholds alertThresholds;

    public RecordService(RecordRepository repository, RecordSummaryRepository summaries, Validator validator,
                         NotificationPublisher notificationPublisher, AlertPublisher alertPublisher,
                         ValueThresholds alertThresholds) {
        this.repository = repository;
        this.summaries = summaries;
        this.validator = validator;
        this.notificationPublisher = notificationPublisher;
        this.alertPublisher = alertPublisher;
        this.alertThresholds = alertThresholds;
    }

    /**
     * Stores the record and publishes its events.
     *
     * @throws DuplicateRecordException if a record with the same {@code recordId} has already been processed;
     *                                  the stored record is left unchanged
     */
    @Transactional
    public void process(DataRecord record) {
        if (!store(record)) {
            throw new DuplicateRecordException(record.recordId());
        }
    }

    /** Checks the record's constraints; returns field name → message, empty if the record is valid. */
    public Map<String, String> validate(DataRecord record) {
        Map<String, String> errors = new TreeMap<>();
        for (ConstraintViolation<DataRecord> violation : validator.validate(record)) {
            errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return errors;
    }

    /**
     * Finds one page of the records matching the query, grouped by destination id in result order. Each group's
     * totals cover all of that destination's records matching the filter, including those on other pages.
     * Both run in one snapshot, so a page and its totals are consistent with each other.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RecordPage findGroupedByDestination(RecordQuery query) {
        // One record beyond the limit tells whether there is a next page
        List<DataRecord> records = repository.find(query.withLimit(query.limit() + 1));
        if (records.size() <= query.limit()) {
            return new RecordPage(groupByDestination(records, query.filter()), null);
        }
        List<DataRecord> page = records.subList(0, query.limit());
        return new RecordPage(groupByDestination(page, query.filter()), RecordCursor.after(page.getLast()));
    }

    /**
     * Inserts the record and publishes its events. The duplicate check and the insert are one atomic statement,
     * so concurrent requests with the same recordId can't both get through.
     *
     * @return {@code true} if stored, {@code false} if a record with the same {@code recordId} already exists
     */
    private boolean store(DataRecord record) {
        if (!repository.insertIfAbsent(record)) {
            return false;
        }
        RecordSummary previous = addToRunningSummary(record);
        publishEvents(record, previous);
        return true;
    }

    /**
     * Adds the record to the running summary of its destination and reference and returns the summary as it was
     * before. The summary stays locked until the transaction ends, so concurrent records for the same destination and
     * reference are summarized one after another, each seeing all earlier ones; the cost doesn't grow with history.
     */
    private RecordSummary addToRunningSummary(DataRecord record) {
        RecordSummary previous = summaries.lock(record.destinationId(), record.reference());
        summaries.update(record.destinationId(), record.reference(), previous.including(record));
        return previous;
    }

    private void publishEvents(DataRecord record, RecordSummary previous) {
        notificationPublisher.publish(new RecordProcessedEvent(record, previous));
        alertThresholds.exceededBy(record)
                .ifPresent(threshold -> alertPublisher.publish(new RecordAlertEvent(record, threshold)));
    }

    private List<RecordGroup> groupByDestination(List<DataRecord> records, RecordFilter filter) {
        // Records come ordered by destination, so a LinkedHashMap keeps the groups in result order
        Map<String, List<DataRecord>> byDestination = records.stream()
                .collect(Collectors.groupingBy(DataRecord::destinationId, LinkedHashMap::new, Collectors.toList()));
        Map<String, Map<String, BigDecimal>> totals = repository.totalsByDestination(filter, byDestination.keySet());

        return byDestination.entrySet().stream()
                .map(group -> new RecordGroup(
                        group.getKey(), totals.getOrDefault(group.getKey(), Map.of()), group.getValue()))
                .toList();
    }
}
