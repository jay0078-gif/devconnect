package com.devconnect.backend.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    // Type of event: POST_LIKED, POST_CREATED, USER_FOLLOWED
    private String type;

    // Who triggered the event (e.g. user who liked)
    private Long actorId;
    private String actorUsername;

    // Who should receive the notification
    private Long recipientId;

    // What the event is about
    private Long postId;
    private String message;
}