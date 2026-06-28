package com.example.platform.render.domain.template.composite;

import java.util.List;

/**
 * Validation result for composite template.
 * Internal domain model.
 */
public record CompositeTemplateValidationResult(
        boolean valid,
        List<CompositeTemplateValidationError> errors) {

    public static CompositeTemplateValidationResult success() {
        return new CompositeTemplateValidationResult(true, List.of());
    }

    public static CompositeTemplateValidationResult failure(List<CompositeTemplateValidationError> errors) {
        return new CompositeTemplateValidationResult(false, errors);
    }
}
