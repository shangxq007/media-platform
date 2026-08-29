package com.example.platform.render.domain.effect;

import java.util.Map;
import java.util.Objects;

/**
 * Issue in a baseline effect plan.
 * Immutable. Internal domain model.
 */
public record BaselineEffectPlanIssue(
        BaselineEffectPlanIssueCode code,
        BaselineEffectPlanIssueSeverity severity,
        String message,
        Map<String, String> safeMetadata
) {
    public BaselineEffectPlanIssue {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(message, "message must not be null");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    public static BaselineEffectPlanIssue blocking(BaselineEffectPlanIssueCode code, String message) {
        return new BaselineEffectPlanIssue(code, BaselineEffectPlanIssueSeverity.BLOCKING, message, Map.of());
    }

    public static BaselineEffectPlanIssue error(BaselineEffectPlanIssueCode code, String message) {
        return new BaselineEffectPlanIssue(code, BaselineEffectPlanIssueSeverity.ERROR, message, Map.of());
    }

    public static BaselineEffectPlanIssue warning(BaselineEffectPlanIssueCode code, String message) {
        return new BaselineEffectPlanIssue(code, BaselineEffectPlanIssueSeverity.WARNING, message, Map.of());
    }

    public static BaselineEffectPlanIssue info(BaselineEffectPlanIssueCode code, String message) {
        return new BaselineEffectPlanIssue(code, BaselineEffectPlanIssueSeverity.INFO, message, Map.of());
    }
}
