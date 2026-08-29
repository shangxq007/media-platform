package com.example.platform.render.domain.plan;

import java.util.Map;
import java.util.Objects;

/**
 * Issue in a basic render plan.
 * Immutable. Internal domain model.
 */
public record BasicRenderPlanIssue(
        BasicRenderPlanIssueCode code,
        BasicRenderPlanIssueSeverity severity,
        String message,
        Map<String, String> safeMetadata
) {
    public BasicRenderPlanIssue {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(message, "message must not be null");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    public static BasicRenderPlanIssue blocking(BasicRenderPlanIssueCode code, String message) {
        return new BasicRenderPlanIssue(code, BasicRenderPlanIssueSeverity.BLOCKING, message, Map.of());
    }

    public static BasicRenderPlanIssue error(BasicRenderPlanIssueCode code, String message) {
        return new BasicRenderPlanIssue(code, BasicRenderPlanIssueSeverity.ERROR, message, Map.of());
    }

    public static BasicRenderPlanIssue warning(BasicRenderPlanIssueCode code, String message) {
        return new BasicRenderPlanIssue(code, BasicRenderPlanIssueSeverity.WARNING, message, Map.of());
    }

    public static BasicRenderPlanIssue info(BasicRenderPlanIssueCode code, String message) {
        return new BasicRenderPlanIssue(code, BasicRenderPlanIssueSeverity.INFO, message, Map.of());
    }
}
