package com.devconnect.backend.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationConsumer {

    // This method runs in a SEPARATE THREAD automatically
    // Kafka calls it whenever a new message arrives on "notifications" topic
    // In production: you'd save to DB, send push notification, send email
    @KafkaListener(topics = "notifications", groupId = "devconnect-group")
    public void handleNotification(NotificationEvent event) {
        log.info("KAFKA CONSUMER — received {} notification", event.getType());
        log.info("  → Actor: {} (id: {})", event.getActorUsername(), event.getActorId());
        log.info("  → Recipient id: {}", event.getRecipientId());
        log.info("  → Message: {}", event.getMessage());

        // TODO week 6: save to notifications table in DB
        // TODO week 6: send WebSocket push to recipient
    }

    @KafkaListener(topics = "post-events", groupId = "devconnect-group")
    public void handlePostEvent(NotificationEvent event) {
        log.info("KAFKA CONSUMER — post event: {}", event.getType());
        log.info("  → Post id: {}, Actor: {}", event.getPostId(), event.getActorUsername());

        // TODO week 6: update feed cache for all followers
    }
}