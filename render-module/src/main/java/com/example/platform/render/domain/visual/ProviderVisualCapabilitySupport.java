package com.example.platform.render.domain.visual;

import java.util.Map;
import java.util.Objects;

/**
 * Provider's declared support for a specific visual capability.
 * Immutable record. Internal domain model.
 */
public record ProviderVisualCapabilitySupport(
        String providerId,
        VisualCapabilityId visualCapabilityId,
        VisualCapabilityCategory category,
        VisualCapabilityStatus status,
        VisualConsistencyLevel consistencyLevel,
        VisualFallbackBehavior fallbackBehavior,
        boolean autoDispatchAllowed,
        boolean productionAllowed,
        Map<String, String> safeMetadata
) {
    public ProviderVisualCapabilitySupport {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(visualCapabilityId, "visualCapabilityId must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(consistencyLevel, "consistencyLevel must not be null");
        Objects.requireNonNull(fallbackBehavior, "fallbackBehavior must not be null");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    /**
     * Returns true if this provider support allows production use.
     */
    public boolean isProductionEligible() {
        return productionAllowed && status.isProductionAllowed();
    }

    /**
     * Returns true if auto-dispatch is allowed.
     */
    public boolean isAutoDispatchEligible() {
        return autoDispatchAllowed && status.isAutoDispatchAllowed();
    }
}
