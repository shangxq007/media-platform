package com.example.platform.render.domain.render.local;

import java.util.Map;
import java.util.Objects;

/**
 * An issue observed during local render smoke execution.
 */
public record LocalRenderSmokeIssue(
        LocalRenderSmokeIssueSeverity severity,
        LocalRenderSmokeIssueCode code,
        String message,
        Map<String, String> metadata
) {
    public LocalRenderSmokeIssue {
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static LocalRenderSmokeIssue info(LocalRenderSmokeIssueCode code, String message) {
        return new LocalRenderSmokeIssue(LocalRenderSmokeIssueSeverity.INFO, code, message, Map.of());
    }

    public static LocalRenderSmokeIssue warning(LocalRenderSmokeIssueCode code, String message) {
        return new LocalRenderSmokeIssue(LocalRenderSmokeIssueSeverity.WARNING, code, message, Map.of());
    }

    public static LocalRenderSmokeIssue error(LocalRenderSmokeIssueCode code, String message) {
        return new LocalRenderSmokeIssue(LocalRenderSmokeIssueSeverity.ERROR, code, message, Map.of());
    }

    public static LocalRenderSmokeIssue blocking(LocalRenderSmokeIssueCode code, String message) {
        return new LocalRenderSmokeIssue(LocalRenderSmokeIssueSeverity.BLOCKING, code, message, Map.of());
    }
}
