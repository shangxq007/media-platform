package com.example.platform.render.domain.render.local;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregated report of local render smoke execution(s).
 */
public record LocalRenderSmokeReport(
        String reportId,
        Instant executedAt,
        LocalRenderSmokeStatus overallStatus,
        List<LocalRenderSmokeResult> results,
        int passCount,
        int failCount,
        int skippedCount,
        int notAvailableCount,
        Map<String, String> safeMetadata
) {
    public LocalRenderSmokeReport {
        Objects.requireNonNull(reportId, "reportId must not be null");
        Objects.requireNonNull(executedAt, "executedAt must not be null");
        Objects.requireNonNull(overallStatus, "overallStatus must not be null");
        results = results == null ? List.of() : List.copyOf(results);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
