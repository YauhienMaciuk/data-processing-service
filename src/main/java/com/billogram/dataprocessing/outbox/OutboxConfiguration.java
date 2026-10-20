package com.billogram.dataprocessing.outbox;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the scheduler that runs {@link OutboxRelay}. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class OutboxConfiguration {
}
