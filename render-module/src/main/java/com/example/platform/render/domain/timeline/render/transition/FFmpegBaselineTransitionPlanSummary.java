package com.example.platform.render.domain.timeline.render.transition;

import java.util.Map;

/**
 * Summary of an FFmpeg baseline transition plan.
 * Immutable. Internal domain model.
 */
public record FFmpegBaselineTransitionPlanSummary(
        int totalOperations,
        int baselineOperationCount,
        int pocOperationCount,
        int forbiddenRejectedCount,
        int warningCount,
        Map<String, String> safeMetadata
) {
    public FFmpegBaselineTransitionPlanSummary {
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
