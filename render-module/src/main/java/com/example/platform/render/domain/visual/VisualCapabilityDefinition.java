package com.example.platform.render.domain.visual;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Definition of a visual capability.
 * Immutable record. Internal domain model.
 */
public record VisualCapabilityDefinition(
        VisualCapabilityId id,
        VisualCapabilityCategory category,
        VisualCapabilityStatus status,
        String displayName,
        String description,
        VisualConsistencyLevel defaultConsistency,
        VisualFallbackBehavior defaultFallback,
        VisualCapabilitySafetyLevel safetyLevel,
        List<VisualCapabilityParameterDefinition> parameters,
        List<VisualCapabilityValidationRule> validationRules,
        Map<String, String> safeMetadata
) {
    public VisualCapabilityDefinition {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(defaultConsistency, "defaultConsistency must not be null");
        Objects.requireNonNull(defaultFallback, "defaultFallback must not be null");
        Objects.requireNonNull(safetyLevel, "safetyLevel must not be null");
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        validationRules = validationRules == null ? List.of() : List.copyOf(validationRules);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    /**
     * Returns true if this capability is production-allowed.
     */
    public boolean isProductionAllowed() {
        return status.isProductionAllowed() && safetyLevel != VisualCapabilitySafetyLevel.FORBIDDEN;
    }

    /**
     * Returns true if this capability is auto-dispatch allowed.
     */
    public boolean isAutoDispatchAllowed() {
        return status.isAutoDispatchAllowed() && safetyLevel != VisualCapabilitySafetyLevel.FORBIDDEN;
    }
}
