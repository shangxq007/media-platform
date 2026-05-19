package com.example.platform.analytics.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record UserHabitsResponse(
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
) {}
