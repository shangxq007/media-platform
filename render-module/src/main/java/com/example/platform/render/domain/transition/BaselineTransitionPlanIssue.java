package com.example.platform.render.domain.transition;

import java.util.Map;
import java.util.Objects;

/**
 * Issue in a baseline transition plan.
 * Immutable. Internal domain model.
 */
public record BaselineTransitionPlanIssue(
        BaselineTransitionPlanIssueCode code,
        BaselineTransitionPlanIssueSeverity severity,
        String message,
        Map<String, String> safeMetadata
) {
    public BaselineTransitionPlanIssue {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(message, "message must not be null");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    public static BaselineTransitionPlanIssue blocking(BaselineTransitionPlanIssueCode code, String message) {
        return new BaselineTransitionPlanIssue(code, BaselineTransitionPlanIssueSeverity.BLOCKING, message, Map.of());
    }

    public static BaselineTransitionPlanIssue error(BaselineTransitionPlanIssueCode code, String message) {
        return new BaselineTransitionPlanIssue(code, BaselineTransitionPlanIssueSeverity.ERROR, message, Map.of());
    }

    public static BaselineTransitionPlanIssue warning(BaselineTransitionPlanIssueCode code, String message) {
        return new BaselineTransitionPlanIssue(code, BaselineTransitionPlanIssueSeverity.WARNING, message, Map.of());
    }

    public static BaselineTransitionPlanIssue info(BaselineTransitionPlanIssueCode code, String message) {
        return new BaselineTransitionPlanIssue(code, BaselineTransitionPlanIssueSeverity.INFO, message, Map.of());
    }
}
