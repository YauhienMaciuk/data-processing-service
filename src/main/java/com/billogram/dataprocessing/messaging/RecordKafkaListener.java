package com.billogram.dataprocessing.messaging;

import com.billogram.dataprocessing.domain.DataRecord;
import com.billogram.dataprocessing.domain.DuplicateRecordException;
import com.billogram.dataprocessing.service.RecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * Consumes records published as JSON messages. A record whose {@code recordId} has already been processed is
 * skipped. A message that can't be read as a record throws {@link tools.jackson.core.JacksonException} and an invalid
 * record throws {@link InvalidRecordException}; the error handler in {@link RecordKafkaConfiguration} sends both to
 * the dead-letter topic without retrying.
 */
@Component
public class RecordKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(RecordKafkaListener.class);

    private final JsonMapper jsonMapper;
    private final RecordService recordService;

    public RecordKafkaListener(JsonMapper jsonMapper, RecordService recordService) {
        this.jsonMapper = jsonMapper;
        this.recordService = recordService;
    }

    @KafkaListener(topics = "${records.kafka.topic}")
    public void onMessage(String message) {
        DataRecord record = jsonMapper.readValue(message, DataRecord.class);
        requireValid(record);
        try {
            recordService.process(record);
        } catch (DuplicateRecordException e) {
            // Kafka delivers at least once, so redelivered messages are expected and harmless
            log.info("Skipping record '{}': already processed", e.getRecordId());
        }
    }

    private void requireValid(DataRecord record) {
        if (record == null) {
            // Jackson reads the JSON literal null as no record at all
            throw new InvalidRecordException(Map.of("record", "must be a JSON object"));
        }
        Map<String, String> errors = recordService.validate(record);
        if (!errors.isEmpty()) {
            throw new InvalidRecordException(errors);
        }
    }
}
