package com.devconnect.backend.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationProducer {

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    // Send event to Kafka topic — fire and forget, non-blocking
    // The controller doesn't wait for this — Kafka handles it async
    public void sendNotification(NotificationEvent event) {
        log.info("KAFKA PRODUCER — sending {} event to notifications topic", event.getType());
        kafkaTemplate.send("notifications", event.getRecipientId().toString(), event);
        // Key = recipientId ensures all notifications for same user
        // go to same partition — ordered delivery per user
    }

    public void sendPostEvent(NotificationEvent event) {
        log.info("KAFKA PRODUCER — sending {} event to post-events topic", event.getType());
        kafkaTemplate.send("post-events", event.getPostId().toString(), event);
    }
}