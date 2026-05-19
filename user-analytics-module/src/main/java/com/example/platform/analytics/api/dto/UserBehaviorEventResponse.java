package com.example.platform.analytics.api.dto;

import java.time.Instant;
import java.util.Map;

public record UserBehaviorEventResponse(
        String eventId,
        String tenantId,
        String userId,
        String eventType,
        String action,
        String resourceType,
        String resourceId,
        Map<String, String> metadata,
        Instant occurredAt
) {}
