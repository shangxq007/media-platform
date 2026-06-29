package com.example.platform.render.domain.visual;

import java.util.Map;
import java.util.Objects;

/**
 * Validation rule for a visual capability parameter.
 * Immutable record. Internal domain model.
 */
public record VisualCapabilityValidationRule(
        String parameterName,
        String ruleType,
        String ruleExpression,
        String errorMessage,
        Map<String, String> safeMetadata
) {
    public VisualCapabilityValidationRule {
        Objects.requireNonNull(parameterName, "parameterName must not be null");
        Objects.requireNonNull(ruleType, "ruleType must not be null");
        if (parameterName.isBlank()) throw new IllegalArgumentException("parameterName must not be blank");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
