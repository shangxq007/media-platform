package com.example.platform.render.domain.plan;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Basic timeline render plan.
 * Pure, side-effect-free. Internal domain model.
 *
 * <p>Contains only internal stage/step vocabulary — no raw provider commands,
 * no shell commands, no provider-specific parameters.</p>
 */
public record BasicRenderPlan(
        BasicRenderPlanId id,
        BasicRenderPlanStatus status,
        List<BasicRenderStage> stages,
        BasicRenderPlanSummary summary,
        List<BasicRenderPlanIssue> issues,
        Map<String, String> safeMetadata
) {
    public BasicRenderPlan {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
        stages = stages == null ? List.of() : List.copyOf(stages);
        issues = issues == null ? List.of() : List.copyOf(issues);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
