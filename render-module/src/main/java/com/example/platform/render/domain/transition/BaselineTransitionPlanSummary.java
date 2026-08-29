package com.example.platform.render.domain.transition;

import java.util.Map;

/**
 * Summary of a baseline transition plan.
 * Immutable. Internal domain model.
 */
public record BaselineTransitionPlanSummary(
        int totalOperations,
        int baselineOperationCount,
        int pocOperationCount,
        int forbiddenRejectedCount,
        int warningCount,
        Map<String, String> safeMetadata
) {
    public BaselineTransitionPlanSummary {
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
