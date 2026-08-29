package com.example.platform.render.domain.effect;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of baseline effect planning.
 * Immutable. Internal domain model.
 */
public record BaselineEffectPlanningResult(
        BaselineEffectPlanningResultStatus status,
        BaselineEffectPlan plan,
        List<BaselineEffectPlanIssue> issues,
        Map<String, String> safeMetadata
) {
    public BaselineEffectPlanningResult {
        Objects.requireNonNull(status, "status must not be null");
        issues = issues == null ? List.of() : List.copyOf(issues);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    public static BaselineEffectPlanningResult planned(BaselineEffectPlan plan) {
        return new BaselineEffectPlanningResult(
                BaselineEffectPlanningResultStatus.PLANNED, plan, List.of(), Map.of());
    }

    public static BaselineEffectPlanningResult validationFailed(
            BaselineEffectPlan plan, List<BaselineEffectPlanIssue> issues) {
        return new BaselineEffectPlanningResult(
                BaselineEffectPlanningResultStatus.VALIDATION_FAILED, plan, issues, Map.of());
    }

    public static BaselineEffectPlanningResult blocked(List<BaselineEffectPlanIssue> issues) {
        return new BaselineEffectPlanningResult(
                BaselineEffectPlanningResultStatus.BLOCKED, null, issues, Map.of());
    }

    public static BaselineEffectPlanningResult unsupported(List<BaselineEffectPlanIssue> issues) {
        return new BaselineEffectPlanningResult(
                BaselineEffectPlanningResultStatus.UNSUPPORTED, null, issues, Map.of());
    }

    public static BaselineEffectPlanningResult failed(List<BaselineEffectPlanIssue> issues) {
        return new BaselineEffectPlanningResult(
                BaselineEffectPlanningResultStatus.FAILED, null, issues, Map.of());
    }
}
