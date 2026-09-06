package com.example.platform.providerplugin.visual;

import java.util.Map;
import java.util.Objects;

/**
 * Provider-runtime declaration of support for a render-owned visual capability.
 *
 * <p>The capability identifier is an opaque stable key. Render owns the meaning
 * of that key; provider runtime owns its support, dispatch and fallback profile.
 * Keeping this contract free of render classes preserves the one-way module
 * boundary.</p>
 */
public record ProviderVisualCapabilitySupport(
        String providerId,
        String visualCapabilityId,
        ProviderVisualCapabilityCategory category,
        ProviderVisualCapabilityStatus status,
        ProviderVisualConsistencyLevel consistencyLevel,
        ProviderVisualFallbackBehavior fallbackBehavior,
        boolean autoDispatchAllowed,
        boolean productionAllowed,
        Map<String, String> safeMetadata) {

    public ProviderVisualCapabilitySupport {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(visualCapabilityId, "visualCapabilityId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(consistencyLevel, "consistencyLevel");
        Objects.requireNonNull(fallbackBehavior, "fallbackBehavior");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    public boolean isProductionEligible() {
        return productionAllowed && status.isProductionAllowed();
    }

    public boolean isAutoDispatchEligible() {
        return autoDispatchAllowed && status.isAutoDispatchAllowed();
    }
}
