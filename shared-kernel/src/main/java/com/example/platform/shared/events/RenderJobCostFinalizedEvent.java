package com.example.platform.shared.events;

import java.time.Instant;

/**
 * Published when a render job cost is finalized.
 */
public record RenderJobCostFinalizedEvent(
        String renderJobId,
        String tenantId,
        String userId,
        String providerKey,
        String preset,
        double estimatedCost,
        double actualCost,
        String currency,
        Instant finalizedAt) {}
