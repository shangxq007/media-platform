package com.example.platform.render.domain.template;

import java.util.List;

/**
 * Validation result for template application.
 * Internal domain model.
 */
public record TemplateValidationResult(
        boolean valid,
        List<TemplateValidationError> errors) {

    public static TemplateValidationResult success() {
        return new TemplateValidationResult(true, List.of());
    }

    public static TemplateValidationResult failure(List<TemplateValidationError> errors) {
        return new TemplateValidationResult(false, errors);
    }
}
