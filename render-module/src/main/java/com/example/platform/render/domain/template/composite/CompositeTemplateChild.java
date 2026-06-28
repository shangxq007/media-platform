package com.example.platform.render.domain.template.composite;

import com.example.platform.render.domain.template.TemplateDefinitionId;
import com.example.platform.render.domain.template.TemplateVersion;
import java.util.Map;

/**
 * Child reference in a CompositeTemplateDefinition.
 * Internal domain model.
 */
public record CompositeTemplateChild(
        CompositeTemplateChildId id,
        TemplateDefinitionId childTemplateId,
        TemplateVersion childTemplateVersion,
        CompositeTemplateChildOrder order,
        boolean required,
        Map<String, String> safeMetadata) {

    public CompositeTemplateChild {
        if (id == null) throw new IllegalArgumentException("Child ID must not be null");
        if (childTemplateId == null) throw new IllegalArgumentException("Child template ID must not be null");
        if (childTemplateVersion == null) throw new IllegalArgumentException("Child template version must not be null");
        if (order == null) throw new IllegalArgumentException("Child order must not be null");
    }
}
