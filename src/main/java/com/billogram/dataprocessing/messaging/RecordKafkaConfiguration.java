package com.billogram.dataprocessing.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

import java.util.function.BiFunction;

/** Topics and error handling for consuming records from Kafka. */
@Configuration(proxyBeanMethods = false)
public class RecordKafkaConfiguration {

    /** Lets Kafka choose the partition when publishing to the dead-letter topic. */
    private static final int ANY_PARTITION = -1;

    /** Created on startup if missing; a no-op when the topic already exists. */
    @Bean
    NewTopic recordsTopic(RecordKafkaProperties properties) {
        return TopicBuilder.name(properties.topic()).build();
    }

    @Bean
    NewTopic recordsDeadLetterTopic(RecordKafkaProperties properties) {
        return TopicBuilder.name(properties.deadLetterTopic()).build();
    }

    /**
     * Retries failed messages with a fixed delay, then publishes them to the dead-letter topic.
     * Unreadable messages and invalid records go there immediately, since retrying can't fix them.
     * Spring Boot applies this handler to all {@code @KafkaListener} containers.
     */
    @Bean
    DefaultErrorHandler recordErrorHandler(KafkaTemplate<?, ?> kafkaTemplate, RecordKafkaProperties properties) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(kafkaTemplate, deadLetterDestination(properties.deadLetterTopic()));
        RecordKafkaProperties.Retry retry = properties.retry();

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(recoverer, new FixedBackOff(retry.interval().toMillis(), retry.maxRetries()));
        errorHandler.addNotRetryableExceptions(InvalidRecordException.class, JacksonException.class);
        return errorHandler;
    }

    static BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> deadLetterDestination(String deadLetterTopic) {
        return (message, exception) -> new TopicPartition(deadLetterTopic, ANY_PARTITION);
    }
}
