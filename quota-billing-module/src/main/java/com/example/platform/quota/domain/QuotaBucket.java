package com.example.platform.quota.domain;

import java.time.Instant;

public record QuotaBucket(
        String id,
        String tenantId,
        String featureCode,
        long limit,
        String period,
        long currentUsage,
        Instant createdAt,
        Instant updatedAt
) {
    public QuotaBucket {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (featureCode == null || featureCode.isBlank()) throw new IllegalArgumentException("featureCode is required");
        if (limit < 0) throw new IllegalArgumentException("limit must be non-negative");
        if (currentUsage < 0) throw new IllegalArgumentException("currentUsage must be non-negative");
    }

    public QuotaBucket withUsage(long newUsage) {
        return new QuotaBucket(id, tenantId, featureCode, limit, period, newUsage, createdAt, Instant.now());
    }

    public double usageRatio() {
        return limit == 0 ? 0.0 : (double) currentUsage / (double) limit;
    }

    public boolean isExceeded() {
        return currentUsage >= limit;
    }
}
