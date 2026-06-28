package com.example.platform.render.domain.workflow;

import com.example.platform.render.domain.template.TemplateApplicationRequest;
import com.example.platform.render.domain.template.TemplateDefinitionId;
import com.example.platform.render.domain.template.TemplateVersion;
import java.util.Map;

/**
 * Specification for an APPLY_TEMPLATE workflow step.
 * Internal domain model. References template application semantics.
 */
public record WorkflowTemplateApplicationStepSpec(
        TemplateDefinitionId templateId,
        TemplateVersion templateVersion,
        TemplateApplicationRequest templateApplicationRequest,
        Map<String, String> safeMetadata) {

    public WorkflowTemplateApplicationStepSpec {
        if (templateId == null)
            throw new IllegalArgumentException("Template ID must not be null");
    }
}
