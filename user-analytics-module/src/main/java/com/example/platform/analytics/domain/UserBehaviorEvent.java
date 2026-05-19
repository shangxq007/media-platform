package com.example.platform.analytics.domain;

import java.time.Instant;
import java.util.Map;

public record UserBehaviorEvent(
        String eventId,
        String tenantId,
        String userId,
        String eventType,
        String action,
        String resourceType,
        String resourceId,
        Map<String, String> metadata,
        Instant occurredAt
) {
    public UserBehaviorEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
