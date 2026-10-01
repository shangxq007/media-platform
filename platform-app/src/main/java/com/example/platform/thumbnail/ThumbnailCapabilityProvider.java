package com.example.platform.thumbnail;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Stable typed media.thumbnail invocation contract.
 *
 * <p>Provider identity model is the same as {@code CoverImageCapabilityProvider}: a
 * capability-independent provider <em>family</em> ({@code providerId}) plus one implementation
 * identity ({@code providerImplementationId}), and the provider declares the capabilities it
 * serves as a <em>list</em>. The capability/&lt;-&gt;provider relation is therefore explicitly
 * N:M: one provider may declare several capabilities, and one capability may be served by several
 * providers.
 */
public interface ThumbnailCapabilityProvider {
    Manifest manifest();
    Result extract(String capabilityId, ThumbnailContracts.Request request, byte[] input,
            BooleanSupplier cancelled);

    /**
     * One capability declared by a provider, with the capability contract version it implements.
     */
    record CapabilityDeclaration(String capabilityId, String contractVersion) {

        public CapabilityDeclaration {
            ThumbnailContracts.require(capabilityId, "capabilityId");
            ThumbnailContracts.require(contractVersion, "contractVersion");
        }
    }

    /**
     * Provider declaration aligned with {@code CoverImageCapabilityProvider.Manifest}: a
     * capability-independent provider <em>family</em> identity plus the one implementation identity
     * that runs inside it ({@code providerId} and {@code providerImplementationId} are separate
     * slots, both required), and a capability-declaration <em>list</em>. Capability ids are unique
     * within one provider and the list must not be empty.
     */
    record Manifest(String providerId, String providerImplementationId, String providerVersion,
            List<CapabilityDeclaration> capabilities, String executableToolchain,
            Set<String> inputFormats, Set<String> outputFormats,
            double minimumTimestampSeconds, double maximumTimestampSeconds,
            int minimumWidth, int maximumWidth, long maximumInputBytes,
            int timeoutSeconds, String trustRequirement, String runtimeRequirement) {

        public Manifest {
            ThumbnailContracts.require(providerId, "providerId");
            ThumbnailContracts.require(providerImplementationId, "providerImplementationId");
            ThumbnailContracts.require(providerVersion, "providerVersion");
            Objects.requireNonNull(capabilities, "capabilities");
            if (capabilities.isEmpty()) {
                throw new IllegalArgumentException("provider must declare at least one capability");
            }
            Set<String> declared = new LinkedHashSet<>();
            for (CapabilityDeclaration capability : capabilities) {
                Objects.requireNonNull(capability, "capability declaration");
                if (!declared.add(capability.capabilityId())) {
                    throw new IllegalArgumentException(
                            "duplicate capability declaration: " + capability.capabilityId());
                }
            }
            capabilities = List.copyOf(capabilities);
        }

        /** True when this provider declares the given capability. */
        public boolean supports(String capabilityId) {
            return capabilities.stream()
                    .anyMatch(declared -> declared.capabilityId().equals(capabilityId));
        }
    }
    record Result(byte[] bytes, String contentType, String failureCode) {
        public static Result success(byte[] bytes, String contentType) { return new Result(bytes, contentType, null); }
        public static Result failure(String code) { return new Result(null, null, code); }
        public boolean succeeded() { return bytes != null && failureCode == null; }
    }
}
