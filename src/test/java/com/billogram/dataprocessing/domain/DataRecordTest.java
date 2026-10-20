package com.billogram.dataprocessing.domain;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static com.billogram.dataprocessing.domain.TestRecords.TIME;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies the Bean Validation constraints declared on {@link DataRecord}. */
class DataRecordTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private final Validator validator = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeFactory() {
        VALIDATOR_FACTORY.close();
    }

    private Set<String> invalidFields(DataRecord record) {
        return validator.validate(record).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static DataRecord withStrings(String recordId, String sourceId, String destinationId, String unit,
                                          String reference) {
        return new DataRecord(recordId, TIME, sourceId, destinationId, RecordType.POSITIVE, BigDecimal.ONE, unit, reference);
    }

    @Test
    void completeRecordIsValid() {
        assertEquals(Set.of(), invalidFields(withStrings("r1", "src", "dst", "EUR", "ref")));
    }

    @Test
    void referenceIsOptional() {
        assertEquals(Set.of(), invalidFields(withStrings("r1", "src", "dst", "EUR", null)));
    }

    @Test
    void requiredFieldsMustBePresent() {
        DataRecord empty = new DataRecord(null, null, null, null, null, null, null, null);

        assertEquals(Set.of("recordId", "time", "sourceId", "destinationId", "type", "value", "unit"), invalidFields(empty));
    }

    @Test
    void requiredTextFieldsMustNotBeBlank() {
        assertEquals(Set.of("recordId", "sourceId", "destinationId", "unit"), invalidFields(withStrings(" ", "", " ", "", null)));
    }

    @Test
    void textFieldsMustFitTheirColumns() {
        String tooLongFor255 = "x".repeat(256);
        String tooLongFor64 = "x".repeat(65);

        assertEquals(Set.of("recordId", "sourceId", "destinationId", "unit", "reference"),
                invalidFields(withStrings(tooLongFor255, tooLongFor255, tooLongFor255, tooLongFor64, tooLongFor255)));
    }

    @Test
    void textFieldsAtTheirColumnLimitAreValid() {
        String max255 = "x".repeat(255);

        assertEquals(Set.of(), invalidFields(withStrings(max255, max255, max255, "x".repeat(64), max255)));
    }
}
