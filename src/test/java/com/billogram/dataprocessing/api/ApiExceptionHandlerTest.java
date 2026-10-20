package com.billogram.dataprocessing.api;

import com.billogram.dataprocessing.domain.DataRecord;
import com.billogram.dataprocessing.domain.DuplicateRecordException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.FieldError;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void duplicateRecordBecomes409WithRecordId() {
        ProblemDetail problem = handler.handleDuplicateRecord(new DuplicateRecordException("rec-1"));

        assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        assertEquals("Record with recordId 'rec-1' has already been processed", problem.getDetail());
        assertEquals("rec-1", problem.getProperties().get("recordId"));
    }

    @Test
    void validationFailureBecomes400ListingEachInvalidFieldOnce() throws NoSuchMethodException {
        MapBindingResult bindingResult = new MapBindingResult(new HashMap<>(), "dataRecord");
        bindingResult.addError(fieldError("sourceId", "must not be blank", false));
        bindingResult.addError(fieldError("sourceId", "size must be between 0 and 255", false));  // second error: ignored
        bindingResult.addError(fieldError("type", "Failed to convert property value of type 'java.lang.String'", true));
        MethodParameter parameter = new MethodParameter(RecordController.class.getMethod("process", DataRecord.class), 0);

        ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                new MethodArgumentNotValidException(parameter, bindingResult),
                new HttpHeaders(), HttpStatus.BAD_REQUEST, new ServletWebRequest(new MockHttpServletRequest()));

        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Validation failed", problem.getDetail());
        assertEquals(Map.of("sourceId", "must not be blank", "type", "invalid value"), problem.getProperties().get("errors"));
    }

    private static FieldError fieldError(String field, String message, boolean bindingFailure) {
        return new FieldError("dataRecord", field, null, bindingFailure, null, null, message);
    }
}
