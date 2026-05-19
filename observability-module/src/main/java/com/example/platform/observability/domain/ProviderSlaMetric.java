package com.example.platform.observability.domain;

import java.time.OffsetDateTime;

/**
 * SLA metric for a third-party provider.
 */
public record ProviderSlaMetric(
        String metricId,
        String providerKey,
        String providerType,
        double successRate,
        double avgLatencyMs,
        double p99LatencyMs,
        int totalRequests,
        int failedRequests,
        double errorBudgetRemaining,
        OffsetDateTime measuredAt) {

    public boolean isHealthy() {
        return successRate >= 0.95 && avgLatencyMs < 5000;
    }

    public String healthStatus() {
        if (successRate >= 0.99) return "HEALTHY";
        if (successRate >= 0.95) return "DEGRADED";
        if (successRate >= 0.90) return "UNHEALTHY";
        return "CRITICAL";
    }
}
