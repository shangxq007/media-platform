package com.example.platform.render.domain.timeline.render.transition;

import java.util.Map;
import java.util.Objects;

/**
 * Issue in an FFmpeg baseline transition plan.
 * Immutable. Internal domain model.
 */
public record FFmpegBaselineTransitionPlanIssue(
        FFmpegBaselineTransitionPlanIssueCode code,
        FFmpegBaselineTransitionPlanIssueSeverity severity,
        String message,
        Map<String, String> safeMetadata
) {
    public FFmpegBaselineTransitionPlanIssue {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(message, "message must not be null");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    public static FFmpegBaselineTransitionPlanIssue blocking(FFmpegBaselineTransitionPlanIssueCode code, String message) {
        return new FFmpegBaselineTransitionPlanIssue(code, FFmpegBaselineTransitionPlanIssueSeverity.BLOCKING, message, Map.of());
    }

    public static FFmpegBaselineTransitionPlanIssue error(FFmpegBaselineTransitionPlanIssueCode code, String message) {
        return new FFmpegBaselineTransitionPlanIssue(code, FFmpegBaselineTransitionPlanIssueSeverity.ERROR, message, Map.of());
    }

    public static FFmpegBaselineTransitionPlanIssue warning(FFmpegBaselineTransitionPlanIssueCode code, String message) {
        return new FFmpegBaselineTransitionPlanIssue(code, FFmpegBaselineTransitionPlanIssueSeverity.WARNING, message, Map.of());
    }

    public static FFmpegBaselineTransitionPlanIssue info(FFmpegBaselineTransitionPlanIssueCode code, String message) {
        return new FFmpegBaselineTransitionPlanIssue(code, FFmpegBaselineTransitionPlanIssueSeverity.INFO, message, Map.of());
    }
}
