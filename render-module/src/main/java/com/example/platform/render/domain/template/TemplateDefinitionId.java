package com.example.platform.render.domain.template;

/**
 * Stable identifier for a TemplateDefinition.
 * Internal domain model.
 */
public record TemplateDefinitionId(String value) {
    public TemplateDefinitionId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("TemplateDefinitionId must not be blank");
    }
}
