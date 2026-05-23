package com.example.platform.shared.events;

import java.time.Instant;
import java.util.List;

/**
 * Published when incremental reuse detects content-hash mismatch and forces re-execution.
 * Consumed by notification-module (in-app / email / webhook channels) and optional outbound webhook.
 */
public record RenderCacheHashInvalidatedEvent(
        String renderJobId,
        String projectId,
        String tenantId,
        String baseJobId,
        List<String> invalidatedTaskIds,
        int invalidatedCount,
        Instant detectedAt) {}
