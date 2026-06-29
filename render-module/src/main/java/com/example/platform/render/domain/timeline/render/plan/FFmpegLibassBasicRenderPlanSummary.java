package com.example.platform.render.domain.timeline.render.plan;

import java.util.Map;

/**
 * Summary of an FFmpeg/libass basic render plan.
 * Immutable. Internal domain model.
 */
public record FFmpegLibassBasicRenderPlanSummary(
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
    public FFmpegLibassBasicRenderPlanSummary {
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
