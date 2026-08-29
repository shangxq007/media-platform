package com.example.platform.render.domain.plan;

import java.util.Map;

/**
 * Summary of a basic render plan.
 * Immutable. Internal domain model.
 */
public record BasicRenderPlanSummary(
        int totalStages,
        int totalSteps,
        int validationStepCount,
        int clipStepCount,
        int effectStepCount,
        int transitionStepCount,
        int captionStepCount,
        int watermarkStepCount,
        int assemblyStepCount,
        int encodingStepCount,
        int verificationStepCount,
        Map<String, String> safeMetadata
) {
    public BasicRenderPlanSummary {
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
