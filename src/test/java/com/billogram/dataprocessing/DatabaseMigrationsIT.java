package com.billogram.dataprocessing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies that every Liquibase change file has run, in file name order. */
@IntegrationTest
class DatabaseMigrationsIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void appliesEveryChangeFileInFileNameOrder() throws IOException {
        List<String> changeFiles = Arrays.stream(new PathMatchingResourcePatternResolver()
                        .getResources("classpath:db/changelog/changes/*.sql"))
                .map(Resource::getFilename)
                .filter(Objects::nonNull)
                .sorted()
                .toList();

        List<String> appliedFiles = jdbc.queryForList(
                        "SELECT filename FROM databasechangelog ORDER BY orderexecuted", String.class).stream()
                .map(path -> path.substring(path.lastIndexOf('/') + 1))
                .toList();

        assertEquals(changeFiles, appliedFiles);
    }
}
