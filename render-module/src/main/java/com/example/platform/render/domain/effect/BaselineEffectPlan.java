package com.example.platform.render.domain.effect;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Baseline effect plan.
 * Pure, side-effect-free. Internal domain model.
 *
 * <p>Contains only internal operation vocabulary — no raw provider expressions,
 * no shell commands, no provider-specific parameters.</p>
 */
public record BaselineEffectPlan(
        BaselineEffectPlanId id,
        BaselineEffectPlanStatus status,
        List<BaselineEffectOperation> operations,
        BaselineEffectPlanSummary summary,
        List<BaselineEffectPlanIssue> issues,
        Map<String, String> safeMetadata
) {
    public BaselineEffectPlan {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
        operations = operations == null ? List.of() : List.copyOf(operations);
        issues = issues == null ? List.of() : List.copyOf(issues);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
