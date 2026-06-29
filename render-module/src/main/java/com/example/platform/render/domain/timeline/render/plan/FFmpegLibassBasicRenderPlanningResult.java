package com.example.platform.render.domain.timeline.render.plan;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of FFmpeg/libass basic render planning.
 * Immutable. Internal domain model.
 */
public record FFmpegLibassBasicRenderPlanningResult(
        FFmpegLibassBasicRenderPlanningResultStatus status,
        FFmpegLibassBasicRenderPlan plan,
        List<FFmpegLibassBasicRenderPlanIssue> issues,
        Map<String, String> safeMetadata
) {
    public FFmpegLibassBasicRenderPlanningResult {
        Objects.requireNonNull(status, "status must not be null");
        issues = issues == null ? List.of() : List.copyOf(issues);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    public static FFmpegLibassBasicRenderPlanningResult planned(FFmpegLibassBasicRenderPlan plan) {
        return new FFmpegLibassBasicRenderPlanningResult(
                FFmpegLibassBasicRenderPlanningResultStatus.PLANNED, plan, List.of(), Map.of());
    }

    public static FFmpegLibassBasicRenderPlanningResult validationFailed(
            FFmpegLibassBasicRenderPlan plan, List<FFmpegLibassBasicRenderPlanIssue> issues) {
        return new FFmpegLibassBasicRenderPlanningResult(
                FFmpegLibassBasicRenderPlanningResultStatus.VALIDATION_FAILED, plan, issues, Map.of());
    }

    public static FFmpegLibassBasicRenderPlanningResult blocked(List<FFmpegLibassBasicRenderPlanIssue> issues) {
        return new FFmpegLibassBasicRenderPlanningResult(
                FFmpegLibassBasicRenderPlanningResultStatus.BLOCKED, null, issues, Map.of());
    }

    public static FFmpegLibassBasicRenderPlanningResult unsupported(List<FFmpegLibassBasicRenderPlanIssue> issues) {
        return new FFmpegLibassBasicRenderPlanningResult(
                FFmpegLibassBasicRenderPlanningResultStatus.UNSUPPORTED, null, issues, Map.of());
    }

    public static FFmpegLibassBasicRenderPlanningResult failed(List<FFmpegLibassBasicRenderPlanIssue> issues) {
        return new FFmpegLibassBasicRenderPlanningResult(
                FFmpegLibassBasicRenderPlanningResultStatus.FAILED, null, issues, Map.of());
    }
}
