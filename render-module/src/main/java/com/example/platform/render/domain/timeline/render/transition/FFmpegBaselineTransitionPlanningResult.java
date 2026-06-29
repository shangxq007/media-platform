package com.example.platform.render.domain.timeline.render.transition;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of FFmpeg baseline transition planning.
 * Immutable. Internal domain model.
 */
public record FFmpegBaselineTransitionPlanningResult(
        FFmpegBaselineTransitionPlanningResultStatus status,
        FFmpegBaselineTransitionPlan plan,
        List<FFmpegBaselineTransitionPlanIssue> issues,
        Map<String, String> safeMetadata
) {
    public FFmpegBaselineTransitionPlanningResult {
        Objects.requireNonNull(status, "status must not be null");
        issues = issues == null ? List.of() : List.copyOf(issues);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    public static FFmpegBaselineTransitionPlanningResult planned(FFmpegBaselineTransitionPlan plan) {
        return new FFmpegBaselineTransitionPlanningResult(
                FFmpegBaselineTransitionPlanningResultStatus.PLANNED, plan, List.of(), Map.of());
    }

    public static FFmpegBaselineTransitionPlanningResult validationFailed(
            FFmpegBaselineTransitionPlan plan, List<FFmpegBaselineTransitionPlanIssue> issues) {
        return new FFmpegBaselineTransitionPlanningResult(
                FFmpegBaselineTransitionPlanningResultStatus.VALIDATION_FAILED, plan, issues, Map.of());
    }

    public static FFmpegBaselineTransitionPlanningResult blocked(List<FFmpegBaselineTransitionPlanIssue> issues) {
        return new FFmpegBaselineTransitionPlanningResult(
                FFmpegBaselineTransitionPlanningResultStatus.BLOCKED, null, issues, Map.of());
    }

    public static FFmpegBaselineTransitionPlanningResult unsupported(List<FFmpegBaselineTransitionPlanIssue> issues) {
        return new FFmpegBaselineTransitionPlanningResult(
                FFmpegBaselineTransitionPlanningResultStatus.UNSUPPORTED, null, issues, Map.of());
    }

    public static FFmpegBaselineTransitionPlanningResult failed(List<FFmpegBaselineTransitionPlanIssue> issues) {
        return new FFmpegBaselineTransitionPlanningResult(
                FFmpegBaselineTransitionPlanningResultStatus.FAILED, null, issues, Map.of());
    }
}
