package com.example.platform.render.domain.timeline.render.effect;

import java.util.Map;
import java.util.Objects;

/**
 * Issue in an FFmpeg baseline effect plan.
 * Immutable. Internal domain model.
 */
public record FFmpegBaselineEffectPlanIssue(
        FFmpegBaselineEffectPlanIssueCode code,
        FFmpegBaselineEffectPlanIssueSeverity severity,
        String message,
        Map<String, String> safeMetadata
) {
    public FFmpegBaselineEffectPlanIssue {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(message, "message must not be null");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    public static FFmpegBaselineEffectPlanIssue blocking(FFmpegBaselineEffectPlanIssueCode code, String message) {
        return new FFmpegBaselineEffectPlanIssue(code, FFmpegBaselineEffectPlanIssueSeverity.BLOCKING, message, Map.of());
    }

    public static FFmpegBaselineEffectPlanIssue error(FFmpegBaselineEffectPlanIssueCode code, String message) {
        return new FFmpegBaselineEffectPlanIssue(code, FFmpegBaselineEffectPlanIssueSeverity.ERROR, message, Map.of());
    }

    public static FFmpegBaselineEffectPlanIssue warning(FFmpegBaselineEffectPlanIssueCode code, String message) {
        return new FFmpegBaselineEffectPlanIssue(code, FFmpegBaselineEffectPlanIssueSeverity.WARNING, message, Map.of());
    }

    public static FFmpegBaselineEffectPlanIssue info(FFmpegBaselineEffectPlanIssueCode code, String message) {
        return new FFmpegBaselineEffectPlanIssue(code, FFmpegBaselineEffectPlanIssueSeverity.INFO, message, Map.of());
    }
}
