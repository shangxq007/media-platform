package com.example.platform.render.domain.plan;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of basic render planning.
 * Immutable. Internal domain model.
 */
public record BasicRenderPlanningResult(
        BasicRenderPlanningResultStatus status,
        BasicRenderPlan plan,
        List<BasicRenderPlanIssue> issues,
        Map<String, String> safeMetadata
) {
    public BasicRenderPlanningResult {
        Objects.requireNonNull(status, "status must not be null");
        issues = issues == null ? List.of() : List.copyOf(issues);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    public static BasicRenderPlanningResult planned(BasicRenderPlan plan) {
        return new BasicRenderPlanningResult(
                BasicRenderPlanningResultStatus.PLANNED, plan, List.of(), Map.of());
    }

    public static BasicRenderPlanningResult validationFailed(
            BasicRenderPlan plan, List<BasicRenderPlanIssue> issues) {
        return new BasicRenderPlanningResult(
                BasicRenderPlanningResultStatus.VALIDATION_FAILED, plan, issues, Map.of());
    }

    public static BasicRenderPlanningResult blocked(List<BasicRenderPlanIssue> issues) {
        return new BasicRenderPlanningResult(
                BasicRenderPlanningResultStatus.BLOCKED, null, issues, Map.of());
    }

    public static BasicRenderPlanningResult unsupported(List<BasicRenderPlanIssue> issues) {
        return new BasicRenderPlanningResult(
                BasicRenderPlanningResultStatus.UNSUPPORTED, null, issues, Map.of());
    }

    public static BasicRenderPlanningResult failed(List<BasicRenderPlanIssue> issues) {
        return new BasicRenderPlanningResult(
                BasicRenderPlanningResultStatus.FAILED, null, issues, Map.of());
    }
}
