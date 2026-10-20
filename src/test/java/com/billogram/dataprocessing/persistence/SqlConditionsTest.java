package com.billogram.dataprocessing.persistence;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlConditionsTest {

    @Test
    void withoutConditionsThereIsNoWhereClause() {
        SqlConditions conditions = new SqlConditions();

        assertEquals("", conditions.where());
        assertEquals(Map.of(), conditions.params());
    }

    @Test
    void requiresAllConditionsAndCollectsTheirParams() {
        SqlConditions conditions = new SqlConditions()
                .and("type = :type", Map.of("type", "positive"))
                .and("destination_id IN (:ids)", Map.of("ids", List.of("dst-1", "dst-2")));

        assertEquals("WHERE type = :type AND destination_id IN (:ids)", conditions.where());
        assertEquals(Map.of("type", "positive", "ids", List.of("dst-1", "dst-2")), conditions.params());
    }

    @Test
    void exposesParamsReadOnly() {
        assertThrows(UnsupportedOperationException.class, () -> new SqlConditions().params().put("type", "positive"));
    }
}
