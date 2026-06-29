package com.example.platform.render.domain.timeline.render.effect;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of FFmpeg baseline effect planning.
 * Immutable. Internal domain model.
 */
public record FFmpegBaselineEffectPlanningResult(
        FFmpegBaselineEffectPlanningResultStatus status,
        FFmpegBaselineEffectPlan plan,
        List<FFmpegBaselineEffectPlanIssue> issues,
        Map<String, String> safeMetadata
) {
    public FFmpegBaselineEffectPlanningResult {
        Objects.requireNonNull(status, "status must not be null");
        issues = issues == null ? List.of() : List.copyOf(issues);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    public static FFmpegBaselineEffectPlanningResult planned(FFmpegBaselineEffectPlan plan) {
        return new FFmpegBaselineEffectPlanningResult(
                FFmpegBaselineEffectPlanningResultStatus.PLANNED, plan, List.of(), Map.of());
    }

    public static FFmpegBaselineEffectPlanningResult validationFailed(
            FFmpegBaselineEffectPlan plan, List<FFmpegBaselineEffectPlanIssue> issues) {
        return new FFmpegBaselineEffectPlanningResult(
                FFmpegBaselineEffectPlanningResultStatus.VALIDATION_FAILED, plan, issues, Map.of());
    }

    public static FFmpegBaselineEffectPlanningResult blocked(List<FFmpegBaselineEffectPlanIssue> issues) {
        return new FFmpegBaselineEffectPlanningResult(
                FFmpegBaselineEffectPlanningResultStatus.BLOCKED, null, issues, Map.of());
    }

    public static FFmpegBaselineEffectPlanningResult unsupported(List<FFmpegBaselineEffectPlanIssue> issues) {
        return new FFmpegBaselineEffectPlanningResult(
                FFmpegBaselineEffectPlanningResultStatus.UNSUPPORTED, null, issues, Map.of());
    }

    public static FFmpegBaselineEffectPlanningResult failed(List<FFmpegBaselineEffectPlanIssue> issues) {
        return new FFmpegBaselineEffectPlanningResult(
                FFmpegBaselineEffectPlanningResultStatus.FAILED, null, issues, Map.of());
    }
}
