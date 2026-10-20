package com.billogram.dataprocessing.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import com.billogram.dataprocessing.domain.ValueThresholds;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
public class AlertConfiguration {

    @Bean
    ValueThresholds alertThresholds(AlertProperties properties) {
        return new ValueThresholds(properties.thresholds());
    }

    /** Created on startup if missing; a no-op when the topic already exists. */
    @Bean
    NewTopic alertsTopic(AlertProperties properties) {
        return TopicBuilder.name(properties.topic()).build();
    }
}
