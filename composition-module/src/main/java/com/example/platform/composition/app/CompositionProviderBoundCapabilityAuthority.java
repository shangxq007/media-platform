package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels.CapabilityAvailability;
import java.util.Optional;

/**
 * Platform-owned seam for registry-backed provider binding. Availability is
 * insufficient for lowering; the implementation must return immutable,
 * provider-bound contract facts from the authoritative registries.
 */
public interface CompositionProviderBoundCapabilityAuthority extends ProviderRegistryBoundary {
    Optional<ProviderBoundCapability> resolveProviderBound(String capabilityId, String version);

    record ProviderBoundCapability(String capabilityId, String capabilityVersion,
            String providerId, String providerContractVersion,
            String inputContract, String inputContractVersion,
            String outputContract, String outputContractVersion) {
        public ProviderBoundCapability {
            require(capabilityId, "capabilityId"); require(capabilityVersion, "capabilityVersion");
            require(providerId, "providerId"); require(providerContractVersion, "providerContractVersion");
            require(inputContract, "inputContract"); require(inputContractVersion, "inputContractVersion");
            require(outputContract, "outputContract"); require(outputContractVersion, "outputContractVersion");
        }
        private static void require(String value, String name) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        }
    }
}
