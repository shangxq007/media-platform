package com.example.platform.render.domain.transition;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Baseline transition plan.
 * Pure, side-effect-free. Internal domain model.
 *
 * <p>Contains only internal operation vocabulary — no raw transition-filter strings,
 * no provider_expression, no shell commands, no provider-specific parameters.</p>
 */
public record BaselineTransitionPlan(
        BaselineTransitionPlanId id,
        BaselineTransitionPlanStatus status,
        List<BaselineTransitionOperation> operations,
        BaselineTransitionPlanSummary summary,
        List<BaselineTransitionPlanIssue> issues,
        Map<String, String> safeMetadata
) {
    public BaselineTransitionPlan {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
        operations = operations == null ? List.of() : List.copyOf(operations);
        issues = issues == null ? List.of() : List.copyOf(issues);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
