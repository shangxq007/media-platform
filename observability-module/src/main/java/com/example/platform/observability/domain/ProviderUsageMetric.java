package com.example.platform.observability.domain;

import java.time.OffsetDateTime;

/**
 * Usage metric for a third-party provider.
 */
public record ProviderUsageMetric(
        String metricId,
        String providerKey,
        String providerType,
        long requestCount,
        long estimatedCost,
        long quotaUsed,
        long quotaRemaining,
        double costPerRequest,
        OffsetDateTime measuredAt) {}
