package com.example.platform.render.domain.render.local;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregated report of a local render execution.
 *
 * @param reportId          unique report id
 * @param executedAt        timestamp of execution
 * @param overallStatus     aggregated status
 * @param results           individual execution results
 * @param passCount         count of PASS results
 * @param failCount         count of FAIL results
 * @param skippedCount      count of SKIPPED results
 * @param notAvailableCount count of NOT_AVAILABLE results
 * @param unsupportedCount  count of UNSUPPORTED results
 * @param safeMetadata      safe metadata
 */
public record LocalRenderExecutionReport(
        String reportId,
        Instant executedAt,
        LocalRenderExecutionStatus overallStatus,
        List<LocalRenderExecutionResult> results,
        int passCount,
        int failCount,
        int skippedCount,
        int notAvailableCount,
        int unsupportedCount,
        Map<String, String> safeMetadata
) {
    public LocalRenderExecutionReport {
        Objects.requireNonNull(reportId, "reportId must not be null");
        Objects.requireNonNull(executedAt, "executedAt must not be null");
        Objects.requireNonNull(overallStatus, "overallStatus must not be null");
        results = results == null ? List.of() : List.copyOf(results);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
