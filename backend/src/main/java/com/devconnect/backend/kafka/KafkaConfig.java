package com.devconnect.backend.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    // Creates the topic automatically if it doesn't exist
    @Bean
    public NewTopic notificationsTopic() {
        return TopicBuilder.name("notifications")
                .partitions(3)   // 3 partitions = 3 consumers can process in parallel
                .replicas(1)     // 1 replica (we only have 1 Kafka broker locally)
                .build();
    }

    @Bean
    public NewTopic postEventsTopic() {
        return TopicBuilder.name("post-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic deleteAccountSagaTopic() {
        return TopicBuilder.name("delete-account-saga")
                .partitions(3)
                .replicas(1)
                .build();
    }
}