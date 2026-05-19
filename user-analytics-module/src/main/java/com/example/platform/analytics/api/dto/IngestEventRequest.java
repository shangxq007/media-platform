package com.example.platform.analytics.api.dto;

import java.time.Instant;
import java.util.Map;

public record IngestEventRequest(
        String userId,
        String eventType,
        String action,
        String resourceType,
        String resourceId,
        Map<String, String> metadata
) {
    public IngestEventRequest {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
    }
}
