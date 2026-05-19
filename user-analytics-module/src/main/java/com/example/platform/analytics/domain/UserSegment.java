package com.example.platform.analytics.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record UserSegment(
        String segmentId,
        String tenantId,
        String name,
        String description,
        Map<String, String> criteria,
        List<String> userIds,
        int userCount,
        Instant computedAt
) {
    public UserSegment {
        if (segmentId == null || segmentId.isBlank()) {
            throw new IllegalArgumentException("segmentId is required");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (criteria == null) {
            criteria = Map.of();
        }
        if (userIds == null) {
            userIds = List.of();
        }
        if (computedAt == null) {
            computedAt = Instant.now();
        }
    }
}
