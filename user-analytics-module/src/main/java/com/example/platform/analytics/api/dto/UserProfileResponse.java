package com.example.platform.analytics.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record UserProfileResponse(
        String profileId,
        String tenantId,
        String userId,
        Set<String> preferredLanguages,
        Map<String, Integer> featureUsageCounts,
        Map<String, Integer> actionCounts,
        int totalSessions,
        int totalActions,
        Instant firstSeenAt,
        Instant lastActiveAt,
        Instant updatedAt
) {}
