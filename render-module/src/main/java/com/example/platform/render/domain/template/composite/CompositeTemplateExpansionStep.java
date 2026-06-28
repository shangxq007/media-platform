package com.example.platform.render.domain.template.composite;

import com.example.platform.render.domain.template.TemplateApplicationRequest;
import com.example.platform.render.domain.template.TemplateDefinitionId;
import java.util.Map;

/**
 * Expansion step for a composite template child.
 * Internal domain model. Does not execute templates.
 */
public record CompositeTemplateExpansionStep(
        CompositeTemplateChildId childId,
        TemplateDefinitionId childTemplateId,
        TemplateApplicationRequest templateApplicationRequest,
        CompositeTemplateExpansionStepStatus status,
        Map<String, String> safeMetadata) {

    public CompositeTemplateExpansionStep {
        if (childId == null) throw new IllegalArgumentException("Child ID must not be null");
        if (childTemplateId == null) throw new IllegalArgumentException("Child template ID must not be null");
    }
}
