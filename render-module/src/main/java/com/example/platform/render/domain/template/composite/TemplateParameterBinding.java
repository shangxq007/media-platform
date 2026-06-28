package com.example.platform.render.domain.template.composite;

import java.util.Map;

/**
 * Maps a parent parameter name to a child parameter name.
 * Internal domain model. Inert expression only.
 */
public record TemplateParameterBinding(
        String parentParameterName,
        CompositeTemplateChildId childId,
        String childParameterName,
        TemplateBindingExpression expression,
        boolean required,
        Map<String, String> safeMetadata) {

    public TemplateParameterBinding {
        if (parentParameterName == null || parentParameterName.isBlank())
            throw new IllegalArgumentException("Parent parameter name must not be blank");
        if (childId == null) throw new IllegalArgumentException("Child ID must not be null");
        if (childParameterName == null || childParameterName.isBlank())
            throw new IllegalArgumentException("Child parameter name must not be blank");
    }
}
