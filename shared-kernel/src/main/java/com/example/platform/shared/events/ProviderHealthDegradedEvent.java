package com.example.platform.shared.events;

import java.time.Instant;

/**
 * Published when a third-party provider health check fails.
 */
public record ProviderHealthDegradedEvent(
        String providerKey,
        String providerType,
        String status,
        double errorRate,
        long latencyMs,
        String reason,
        Instant detectedAt) {}
