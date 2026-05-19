package com.example.platform.billing.domain;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * A reconciliation run comparing internal records with external sources.
 */
public record ReconciliationRun(
        String runId,
        String sourceType,
        String sourceName,
        OffsetDateTime periodStart,
        OffsetDateTime periodEnd,
        int totalRecords,
        int matchedCount,
        int differenceCount,
        String status,
        Map<String, Object> summary,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt) {

    public static ReconciliationRun start(String sourceType, String sourceName,
            OffsetDateTime periodStart, OffsetDateTime periodEnd) {
        return new ReconciliationRun(
                java.util.UUID.randomUUID().toString(),
                sourceType, sourceName, periodStart, periodEnd,
                0, 0, 0, "RUNNING",
                Map.of(), OffsetDateTime.now(), null);
    }

    public ReconciliationRun complete(int total, int matched, int diff, Map<String, Object> summary) {
        return new ReconciliationRun(runId, sourceType, sourceName, periodStart, periodEnd,
                total, matched, diff, "COMPLETED", summary, startedAt, OffsetDateTime.now());
    }
}
