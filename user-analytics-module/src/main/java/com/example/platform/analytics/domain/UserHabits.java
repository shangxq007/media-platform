package com.example.platform.analytics.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record UserHabits(
        String tenantId,
        String userId,
        Map<String, Integer> dailyActivityBuckets,
        Map<String, Integer> weeklyActivityPattern,
        List<String> mostUsedFeatures,
        List<String> mostUsedActions,
        double averageSessionDepth,
        String peakActivityHour,
        String peakActivityDay,
        int retentionDays,
        Instant computedAt
) {
    public UserHabits {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (dailyActivityBuckets == null) {
            dailyActivityBuckets = Map.of();
        }
        if (weeklyActivityPattern == null) {
            weeklyActivityPattern = Map.of();
        }
        if (mostUsedFeatures == null) {
            mostUsedFeatures = List.of();
        }
        if (mostUsedActions == null) {
            mostUsedActions = List.of();
        }
        if (computedAt == null) {
            computedAt = Instant.now();
        }
    }
}
