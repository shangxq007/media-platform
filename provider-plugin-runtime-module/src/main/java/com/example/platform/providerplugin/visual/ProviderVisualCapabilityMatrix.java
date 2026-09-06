package com.example.platform.providerplugin.visual;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Provider-runtime matrix over render-owned visual capability definitions. */
public record ProviderVisualCapabilityMatrix(
        List<ProviderVisualCapabilitySupport> supports, Map<String, String> safeMetadata) {

    public ProviderVisualCapabilityMatrix {
        supports = List.copyOf(Objects.requireNonNull(supports, "supports"));
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    public Optional<ProviderVisualCapabilitySupport> findSupport(
            String providerId, String capabilityId) {
        return supports.stream().filter(s -> s.providerId().equals(providerId)
                && s.visualCapabilityId().equals(capabilityId)).findFirst();
    }

    public List<ProviderVisualCapabilitySupport> findSupportsForCapability(String capabilityId) {
        return supports.stream().filter(s -> s.visualCapabilityId().equals(capabilityId)).toList();
    }

    public List<ProviderVisualCapabilitySupport> findProductionEligible() {
        return supports.stream().filter(ProviderVisualCapabilitySupport::isProductionEligible).toList();
    }

    public boolean hasForbiddenCapabilities() {
        return supports.stream().anyMatch(s -> s.status() == ProviderVisualCapabilityStatus.FORBIDDEN);
    }
}
