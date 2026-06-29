package com.example.platform.render.domain.timeline.render.effect;

import java.util.Map;

/**
 * Summary of an FFmpeg baseline effect plan.
 * Immutable. Internal domain model.
 */
public record FFmpegBaselineEffectPlanSummary(
        int totalOperations,
        int baselineOperationCount,
        int pocOperationCount,
        int forbiddenRejectedCount,
        int warningCount,
        Map<String, String> safeMetadata
) {
    public FFmpegBaselineEffectPlanSummary {
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
