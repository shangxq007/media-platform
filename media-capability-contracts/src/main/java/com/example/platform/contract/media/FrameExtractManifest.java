package com.example.platform.contract.media;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Provider declaration: capability-independent provider identity plus the capability list the
 * provider serves. Capability ids are unique within one provider and the list must not be empty.
 *
 * <p>Capability-neutral by construction: the family ({@code providerId}) and implementation
 * ({@code providerImplementationId}) slots hold no capability identity, and the capabilities this
 * implementation serves are a list.
 */
public record FrameExtractManifest(
        String providerId,
        String providerImplementationId,
        String providerVersion,
        List<FrameExtractCapabilityDeclaration> capabilities,
        String executableToolchain,
        Set<String> inputFormats,
        Set<String> outputFormats,
        double minimumTimestampSeconds,
        double maximumTimestampSeconds,
        int minimumWidth,
        int maximumWidth,
        long maximumInputBytes,
        int timeoutSeconds,
        String trustRequirement,
        String runtimeRequirement) {

    public FrameExtractManifest {
        require(providerId, "providerId");
        require(providerImplementationId, "providerImplementationId");
        require(providerVersion, "providerVersion");
        require(executableToolchain, "executableToolchain");
        require(trustRequirement, "trustRequirement");
        require(runtimeRequirement, "runtimeRequirement");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(inputFormats, "inputFormats");
        Objects.requireNonNull(outputFormats, "outputFormats");
        if (capabilities.isEmpty()) {
            throw new IllegalArgumentException("provider must declare at least one capability");
        }
        Set<String> declared = new LinkedHashSet<>();
        for (FrameExtractCapabilityDeclaration capability : capabilities) {
            Objects.requireNonNull(capability, "capability declaration");
            if (!declared.add(capability.capabilityId())) {
                throw new IllegalArgumentException(
                        "duplicate capability declaration: " + capability.capabilityId());
            }
        }
        capabilities = List.copyOf(capabilities);
        inputFormats = Set.copyOf(inputFormats);
        outputFormats = Set.copyOf(outputFormats);
    }

    /** True when this provider declares the given capability. */
    public boolean supports(String capabilityId) {
        return capabilities.stream().anyMatch(declared -> declared.capabilityId().equals(capabilityId));
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
