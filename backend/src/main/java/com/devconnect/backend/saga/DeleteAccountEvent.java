package com.devconnect.backend.saga;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

// This is the event passed between each saga step via Kafka
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteAccountEvent implements Serializable {

    private String eventType;   // which step we're on
    private Long userId;        // who is being deleted
    private String email;       // for cache eviction
    private String status;      // SUCCESS or FAILED
    private String failedStep;  // which step failed (for compensation)

    // Event types:
    // USER_DELETE_INITIATED → SagaOrchestrator starts the flow
    // POSTS_DELETED         → posts wiped, move to follows
    // FOLLOWS_DELETED       → follows wiped, move to cache
    // CACHE_CLEARED         → Redis cleared, move to user delete
    // USER_DELETED          → saga complete
    // SAGA_FAILED           → compensate
}