package com.example.platform.render.domain.effect;

import java.util.Map;

/**
 * Summary of a baseline effect plan.
 * Immutable. Internal domain model.
 */
public record BaselineEffectPlanSummary(
        int totalOperations,
        int baselineOperationCount,
        int pocOperationCount,
        int forbiddenRejectedCount,
        int warningCount,
        Map<String, String> safeMetadata
) {
    public BaselineEffectPlanSummary {
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
