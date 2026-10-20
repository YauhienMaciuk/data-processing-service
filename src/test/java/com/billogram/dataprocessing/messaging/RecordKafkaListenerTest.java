package com.billogram.dataprocessing.messaging;

import com.billogram.dataprocessing.domain.DataRecord;
import com.billogram.dataprocessing.domain.DuplicateRecordException;
import com.billogram.dataprocessing.domain.RecordType;
import com.billogram.dataprocessing.service.RecordService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordKafkaListenerTest {

    private static final String MESSAGE = """
            {"recordId": "r1", "time": "2026-10-20T10:00:00Z", "sourceId": "src-1", "destinationId": "dst-1",
             "type": "positive", "value": 12345.678901234567890, "unit": "EUR", "reference": "ref-1"}""";
    private static final DataRecord RECORD = new DataRecord("r1", OffsetDateTime.parse("2026-10-20T10:00:00Z"),
            "src-1", "dst-1", RecordType.POSITIVE, new BigDecimal("12345.678901234567890"), "EUR", "ref-1");

    private final RecordService recordService = mock(RecordService.class);
    private final RecordKafkaListener listener = new RecordKafkaListener(JsonMapper.builder().build(), recordService);

    @Test
    void processesValidRecordWithItsExactValue() {
        when(recordService.validate(any())).thenReturn(Map.of());

        listener.onMessage(MESSAGE);

        verify(recordService).process(RECORD);
    }

    @Test
    void skipsAlreadyProcessedRecordWithoutFailing() {
        when(recordService.validate(any())).thenReturn(Map.of());
        doThrow(new DuplicateRecordException("r1")).when(recordService).process(any());

        assertDoesNotThrow(() -> listener.onMessage(MESSAGE));
    }

    @Test
    void rejectsMessageThatIsNotAJsonRecord() {
        assertThrows(JacksonException.class, () -> listener.onMessage("{not json"));
        assertThrows(JacksonException.class, () -> listener.onMessage(""));
        assertThrows(JacksonException.class, () -> listener.onMessage("[1, 2]"));
        assertThrows(JacksonException.class, () -> listener.onMessage("""
                {"recordId": "r1", "type": "neutral"}"""));

        verify(recordService, never()).process(any());
    }

    @Test
    void rejectsJsonNull() {
        InvalidRecordException exception = assertThrows(InvalidRecordException.class, () -> listener.onMessage("null"));

        assertEquals(Map.of("record", "must be a JSON object"), exception.errors());
        verify(recordService, never()).process(any());
    }

    @Test
    void rejectsRecordThatFailsValidation() {
        when(recordService.validate(any())).thenReturn(Map.of("time", "must not be null"));

        InvalidRecordException exception = assertThrows(InvalidRecordException.class, () -> listener.onMessage("""
                {"recordId": "r1"}"""));

        assertEquals(Map.of("time", "must not be null"), exception.errors());
        verify(recordService, never()).process(any());
    }

    @Test
    void propagatesOtherFailuresSoTheyAreRetried() {
        when(recordService.validate(any())).thenReturn(Map.of());
        doThrow(new DataAccessResourceFailureException("database down")).when(recordService).process(any());

        assertThrows(DataAccessResourceFailureException.class, () -> listener.onMessage(MESSAGE));
    }
}
