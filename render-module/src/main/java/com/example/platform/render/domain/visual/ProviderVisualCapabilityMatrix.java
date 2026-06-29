package com.example.platform.render.domain.visual;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Matrix of provider visual capability support declarations.
 * Immutable record. Internal domain model.
 */
public record ProviderVisualCapabilityMatrix(
        List<ProviderVisualCapabilitySupport> supports,
        Map<String, String> safeMetadata
) {
    public ProviderVisualCapabilityMatrix {
        Objects.requireNonNull(supports, "supports must not be null");
        supports = List.copyOf(supports);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    /**
     * Find support for a specific provider and capability.
     */
    public Optional<ProviderVisualCapabilitySupport> findSupport(
            String providerId, VisualCapabilityId capabilityId) {
        return supports.stream()
                .filter(s -> s.providerId().equals(providerId)
                        && s.visualCapabilityId().equals(capabilityId))
                .findFirst();
    }

    /**
     * Find all supports for a specific capability across providers.
     */
    public List<ProviderVisualCapabilitySupport> findSupportsForCapability(
            VisualCapabilityId capabilityId) {
        return supports.stream()
                .filter(s -> s.visualCapabilityId().equals(capabilityId))
                .toList();
    }

    /**
     * Find all production-eligible supports.
     */
    public List<ProviderVisualCapabilitySupport> findProductionEligible() {
        return supports.stream()
                .filter(ProviderVisualCapabilitySupport::isProductionEligible)
                .toList();
    }

    /**
     * Returns true if the matrix contains any forbidden capabilities.
     */
    public boolean hasForbiddenCapabilities() {
        return supports.stream()
                .anyMatch(s -> s.status() == VisualCapabilityStatus.FORBIDDEN);
    }
}
