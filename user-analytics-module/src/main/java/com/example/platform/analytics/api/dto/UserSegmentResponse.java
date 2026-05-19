package com.example.platform.analytics.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record UserSegmentResponse(
        String segmentId,
        String tenantId,
        String name,
        String description,
        Map<String, String> criteria,
        List<String> userIds,
        int userCount,
        Instant computedAt
) {}
