package com.example.platform.render.domain.template;

/**
 * Semantic version for a template definition.
 * Internal domain model.
 */
public record TemplateVersion(String value) {
    public TemplateVersion {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("TemplateVersion must not be blank");
    }
}
