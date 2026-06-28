package com.example.platform.render.domain.template.composite;

import java.util.List;
import java.util.Map;

/**
 * Expansion plan for a composite template — provider-neutral dry-run.
 * Internal domain model. Does not execute templates or render.
 */
public record CompositeTemplateExpansionPlan(
        CompositeTemplateExpansionPlanId id,
        CompositeTemplateDefinitionId compositeTemplateId,
        List<CompositeTemplateExpansionStep> steps,
        CompositeTemplateValidationResult validationResult,
        Map<String, String> safeMetadata) {

    public CompositeTemplateExpansionPlan {
        if (id == null) throw new IllegalArgumentException("Expansion plan ID must not be null");
        if (compositeTemplateId == null) throw new IllegalArgumentException("Composite template ID must not be null");
    }

    public boolean isValid() {
        return validationResult != null && validationResult.valid();
    }

    public long readyStepCount() {
        return steps.stream()
                .filter(s -> s.status() == CompositeTemplateExpansionStepStatus.READY)
                .count();
    }
}
