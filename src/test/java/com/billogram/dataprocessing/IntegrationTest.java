package com.billogram.dataprocessing;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Starts the full application on a random port against Postgres in Testcontainers.
 * All integration tests share one configuration, so Spring reuses a single context across them.
 * Classes using it should be named {@code *IT} so they run in the {@code integrationTest} Gradle task.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
