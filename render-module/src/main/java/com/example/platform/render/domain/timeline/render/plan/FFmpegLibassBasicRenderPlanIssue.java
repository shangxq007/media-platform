package com.example.platform.render.domain.timeline.render.plan;

import java.util.Map;
import java.util.Objects;

/**
 * Issue in an FFmpeg/libass basic render plan.
 * Immutable. Internal domain model.
 */
public record FFmpegLibassBasicRenderPlanIssue(
        FFmpegLibassBasicRenderPlanIssueCode code,
        FFmpegLibassBasicRenderPlanIssueSeverity severity,
        String message,
        Map<String, String> safeMetadata
) {
    public FFmpegLibassBasicRenderPlanIssue {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(message, "message must not be null");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    public static FFmpegLibassBasicRenderPlanIssue blocking(FFmpegLibassBasicRenderPlanIssueCode code, String message) {
        return new FFmpegLibassBasicRenderPlanIssue(code, FFmpegLibassBasicRenderPlanIssueSeverity.BLOCKING, message, Map.of());
    }

    public static FFmpegLibassBasicRenderPlanIssue error(FFmpegLibassBasicRenderPlanIssueCode code, String message) {
        return new FFmpegLibassBasicRenderPlanIssue(code, FFmpegLibassBasicRenderPlanIssueSeverity.ERROR, message, Map.of());
    }

    public static FFmpegLibassBasicRenderPlanIssue warning(FFmpegLibassBasicRenderPlanIssueCode code, String message) {
        return new FFmpegLibassBasicRenderPlanIssue(code, FFmpegLibassBasicRenderPlanIssueSeverity.WARNING, message, Map.of());
    }

    public static FFmpegLibassBasicRenderPlanIssue info(FFmpegLibassBasicRenderPlanIssueCode code, String message) {
        return new FFmpegLibassBasicRenderPlanIssue(code, FFmpegLibassBasicRenderPlanIssueSeverity.INFO, message, Map.of());
    }
}
