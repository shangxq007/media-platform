package com.example.platform.analytics.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record UserProfile(
        String profileId,
        String tenantId,
        String userId,
        String displayName,
        Set<String> preferredLanguages,
        Map<String, Integer> featureUsageCounts,
        Map<String, Integer> actionCounts,
        int totalSessions,
        int totalActions,
        Instant firstSeenAt,
        Instant lastActiveAt,
        Instant updatedAt
) {
    public UserProfile {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("profileId is required");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (preferredLanguages == null) {
            preferredLanguages = Set.of();
        }
        if (featureUsageCounts == null) {
            featureUsageCounts = Map.of();
        }
        if (actionCounts == null) {
            actionCounts = Map.of();
        }
    }
}
