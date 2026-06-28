package com.example.platform.render.domain.template;

import java.util.List;

/**
 * Reusable parameterized Timeline operation bundle.
 * Internal domain model.
 *
 * <p>Must not contain provider names, storage internals,
 * FFmpeg commands, or Remotion props.</p>
 */
public record TemplateDefinition(
        TemplateDefinitionId id,
        TemplateVersion version,
        TemplateType type,
        TemplateDisplayMetadata metadata,
        List<TemplateTargetRole> targetRoles,
        List<TemplateParameter> parameters,
        List<TemplateOperation> operations,
        List<TemplateConstraint> constraints,
        List<TemplateCapabilityRequirement> requiredCapabilities) {

    public TemplateDefinition {
        if (id == null) throw new IllegalArgumentException("Template ID must not be null");
        if (version == null) throw new IllegalArgumentException("Template version must not be null");
        if (type == null) throw new IllegalArgumentException("Template type must not be null");
    }
}
