package com.example.platform.composition.app;

import java.util.Optional;

/** Typed view of the canonical capability and provider registries. */
public interface CompositionCapabilityResolutionPort {
    Optional<ResolvedCapability> resolve(CompositionExecutionRequest request);

    record ResolvedCapability(String capabilityId, String capabilityVersion, String inputType, String outputType) {
        public ResolvedCapability {
            require(capabilityId, "capabilityId"); require(capabilityVersion, "capabilityVersion");
            require(inputType, "inputType"); require(outputType, "outputType");
        }
        private static void require(String value, String name) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        }
    }
}
