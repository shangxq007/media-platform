package com.example.platform.shared.events;

import java.time.Instant;
import java.util.Map;

/**
 * Published when a reconciliation run completes.
 */
public record ReconciliationCompletedEvent(
        String runId,
        String sourceType,
        int totalRecords,
        int matchedCount,
        int differenceCount,
        String status,
        Map<String, Object> summary,
        Instant completedAt) {}
