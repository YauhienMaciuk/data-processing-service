package com.billogram.dataprocessing.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
public class NotificationConfiguration {

    /** Created on startup if missing; a no-op when the topic already exists. */
    @Bean
    NewTopic notificationsTopic(NotificationProperties properties) {
        return TopicBuilder.name(properties.topic()).build();
    }
}
