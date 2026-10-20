package com.billogram.dataprocessing.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordPropertiesTest {

    private static RecordProperties bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties)).bindOrCreate("records", RecordProperties.class);
    }

    @Test
    void defaultsQueryLimitsTo100PerPageAndAtMost1000() {
        assertEquals(new RecordProperties.Query(100, 1000), bind(Map.of()).query());
    }

    @Test
    void bindsConfiguredQueryLimits() {
        assertEquals(new RecordProperties.Query(20, 200),
                bind(Map.of("records.query.default-limit", "20", "records.query.max-limit", "200")).query());
    }
}
