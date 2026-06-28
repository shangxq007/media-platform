package com.example.platform.render.domain.template;

/**
 * Parameter definition for a template — declares what a caller can provide.
 * Internal domain model.
 */
public record TemplateParameter(
        String parameterId,
        String name,
        String type,
        boolean required,
        String defaultValue) {

    public TemplateParameter {
        if (parameterId == null || parameterId.isBlank())
            throw new IllegalArgumentException("Parameter ID must not be blank");
    }
}
