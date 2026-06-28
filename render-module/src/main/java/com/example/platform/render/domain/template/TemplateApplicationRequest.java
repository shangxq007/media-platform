package com.example.platform.render.domain.template;

import java.util.List;
import java.util.Map;

/**
 * Binds concrete Products and parameter values to a TemplateDefinition.
 * Internal domain model.
 *
 * <p>No provider selection, no storage internals, no raw commands.</p>
 */
public record TemplateApplicationRequest(
        String projectId,
        TemplateDefinitionId templateId,
        TemplateVersion templateVersion,
        List<TemplateTarget> targets,
        List<TemplateParameter> parameters,
        Map<String, String> safeMetadata) {

    public TemplateApplicationRequest {
        if (projectId == null || projectId.isBlank())
            throw new IllegalArgumentException("Project ID must not be blank");
        if (templateId == null)
            throw new IllegalArgumentException("Template ID must not be null");
        if (targets == null || targets.isEmpty())
            throw new IllegalArgumentException("Targets must not be empty");
    }
}
