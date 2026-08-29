package com.example.platform.render.domain.transition;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of baseline transition planning.
 * Immutable. Internal domain model.
 */
public record BaselineTransitionPlanningResult(
        BaselineTransitionPlanningResultStatus status,
        BaselineTransitionPlan plan,
        List<BaselineTransitionPlanIssue> issues,
        Map<String, String> safeMetadata
) {
    public BaselineTransitionPlanningResult {
        Objects.requireNonNull(status, "status must not be null");
        issues = issues == null ? List.of() : List.copyOf(issues);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    public static BaselineTransitionPlanningResult planned(BaselineTransitionPlan plan) {
        return new BaselineTransitionPlanningResult(
                BaselineTransitionPlanningResultStatus.PLANNED, plan, List.of(), Map.of());
    }

    public static BaselineTransitionPlanningResult validationFailed(
            BaselineTransitionPlan plan, List<BaselineTransitionPlanIssue> issues) {
        return new BaselineTransitionPlanningResult(
                BaselineTransitionPlanningResultStatus.VALIDATION_FAILED, plan, issues, Map.of());
    }

    public static BaselineTransitionPlanningResult blocked(List<BaselineTransitionPlanIssue> issues) {
        return new BaselineTransitionPlanningResult(
                BaselineTransitionPlanningResultStatus.BLOCKED, null, issues, Map.of());
    }

    public static BaselineTransitionPlanningResult unsupported(List<BaselineTransitionPlanIssue> issues) {
        return new BaselineTransitionPlanningResult(
                BaselineTransitionPlanningResultStatus.UNSUPPORTED, null, issues, Map.of());
    }

    public static BaselineTransitionPlanningResult failed(List<BaselineTransitionPlanIssue> issues) {
        return new BaselineTransitionPlanningResult(
                BaselineTransitionPlanningResultStatus.FAILED, null, issues, Map.of());
    }
}
