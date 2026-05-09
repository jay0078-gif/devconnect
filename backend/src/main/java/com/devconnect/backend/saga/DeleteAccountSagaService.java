package com.devconnect.backend.saga;

import com.devconnect.backend.repository.FollowRepository;
import com.devconnect.backend.repository.PostRepository;
import com.devconnect.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteAccountSagaService {

    private final PostRepository postRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String SAGA_TOPIC = "delete-account-saga";

    // ── ENTRY POINT ──
    // Called from UserController DELETE /api/users/me
    // Starts the saga by publishing the first event
    public void initiateDeletion(Long userId, String email) {
        log.info("SAGA START — initiating account deletion for user {}", userId);
        DeleteAccountEvent event = new DeleteAccountEvent(
                "USER_DELETE_INITIATED", userId, email, "SUCCESS", null);
        kafkaTemplate.send(SAGA_TOPIC, event);
    }

    // ── STEP 1: Delete posts ──
    // Triggered by USER_DELETE_INITIATED
    @KafkaListener(topics = SAGA_TOPIC, groupId = "saga-group",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void handleSagaEvent(DeleteAccountEvent event) {
        log.info("SAGA EVENT — {} for user {}",
                event.getEventType(), event.getUserId());

        switch (event.getEventType()) {

            case "USER_DELETE_INITIATED" -> {
                try {
                    // Delete all posts by this user
                    postRepository.deleteByAuthorId(event.getUserId());
                    log.info("SAGA STEP 1 — posts deleted for user {}",
                            event.getUserId());

                    // Publish next step
                    kafkaTemplate.send(SAGA_TOPIC, new DeleteAccountEvent(
                            "POSTS_DELETED", event.getUserId(),
                            event.getEmail(), "SUCCESS", null));
                } catch (Exception e) {
                    log.error("SAGA STEP 1 FAILED — {}", e.getMessage());
                    kafkaTemplate.send(SAGA_TOPIC, new DeleteAccountEvent(
                            "SAGA_FAILED", event.getUserId(),
                            event.getEmail(), "FAILED", "DELETE_POSTS"));
                }
            }

            case "POSTS_DELETED" -> {
                try {
                    // Delete all follow relationships
                    followRepository.deleteByFollowerIdOrFollowingId(
                            event.getUserId(), event.getUserId());
                    log.info("SAGA STEP 2 — follows deleted for user {}",
                            event.getUserId());

                    kafkaTemplate.send(SAGA_TOPIC, new DeleteAccountEvent(
                            "FOLLOWS_DELETED", event.getUserId(),
                            event.getEmail(), "SUCCESS", null));
                } catch (Exception e) {
                    log.error("SAGA STEP 2 FAILED — {}", e.getMessage());
                    kafkaTemplate.send(SAGA_TOPIC, new DeleteAccountEvent(
                            "SAGA_FAILED", event.getUserId(),
                            event.getEmail(), "FAILED", "DELETE_FOLLOWS"));
                }
            }

            case "FOLLOWS_DELETED" -> {
                try {
                    // Clear all Redis cache for this user
                    redisTemplate.delete("users::" + event.getUserId());
                    redisTemplate.delete("users::" + event.getEmail());
                    redisTemplate.delete("feed:user:" + event.getUserId());

                    // Clear any posts cache
                    Set<String> postKeys = redisTemplate.keys("posts::*");
                    if (postKeys != null) redisTemplate.delete(postKeys);

                    log.info("SAGA STEP 3 — Redis cache cleared for user {}",
                            event.getUserId());

                    kafkaTemplate.send(SAGA_TOPIC, new DeleteAccountEvent(
                            "CACHE_CLEARED", event.getUserId(),
                            event.getEmail(), "SUCCESS", null));
                } catch (Exception e) {
                    log.error("SAGA STEP 3 FAILED — {}", e.getMessage());
                    kafkaTemplate.send(SAGA_TOPIC, new DeleteAccountEvent(
                            "SAGA_FAILED", event.getUserId(),
                            event.getEmail(), "FAILED", "CLEAR_CACHE"));
                }
            }

            case "CACHE_CLEARED" -> {
                try {
                    // Final step — delete the user from MySQL
                    userRepository.deleteById(event.getUserId());
                    log.info("SAGA STEP 4 — user {} deleted from MySQL. SAGA COMPLETE",
                            event.getUserId());

                    kafkaTemplate.send(SAGA_TOPIC, new DeleteAccountEvent(
                            "USER_DELETED", event.getUserId(),
                            event.getEmail(), "SUCCESS", null));
                } catch (Exception e) {
                    log.error("SAGA STEP 4 FAILED — {}", e.getMessage());
                    kafkaTemplate.send(SAGA_TOPIC, new DeleteAccountEvent(
                            "SAGA_FAILED", event.getUserId(),
                            event.getEmail(), "FAILED", "DELETE_USER"));
                }
            }

            case "USER_DELETED" -> {
                log.info("SAGA COMPLETE — user {} fully deleted", event.getUserId());
            }

            case "SAGA_FAILED" -> {
                // Compensating transaction — log and alert
                // In production: restore from backup, send email, alert ops team
                log.error("SAGA FAILED at step {} for user {} — manual intervention required",
                        event.getFailedStep(), event.getUserId());
            }
        }
    }
}