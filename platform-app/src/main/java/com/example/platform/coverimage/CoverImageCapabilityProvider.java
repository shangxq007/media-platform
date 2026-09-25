package com.example.platform.coverimage;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Stable typed media.cover-image invocation contract. Provider-specific mechanics (FFmpeg, sandbox
 * toolchain, argument shaping) stay behind this boundary.
 *
 * <p>Model: a provider is identified by a <em>provider family</em> plus one implementation identity,
 * both independent of any capability, and it <em>declares the capabilities it serves</em> as a list
 * (mirrors the platform provider contract shape: provider identity + capability declarations). One
 * provider may therefore serve several capabilities, and one capability may be served by several
 * providers.
 */
public interface CoverImageCapabilityProvider {

    Manifest manifest();

    /**
     * Renders one cover image from an already digest-verified subject artifact.
     *
     * @param capabilityId capability being executed; the provider must declare it. The provider is
     *                     told which capability it is executing so one implementation can serve
     *                     several capabilities with distinct rendering profiles.
     * @param request   canonical cover request
     * @param inputPath path of the digest-verified subject artifact bytes
     * @param cancelled cancellation probe owned by the caller
     */
    Result render(String capabilityId, CoverImageContracts.Request request, Path inputPath,
                  BooleanSupplier cancelled);

    /** One capability declared by a provider, with the capability contract version it implements. */
    record CapabilityDeclaration(String capabilityId, String contractVersion) {

        public CapabilityDeclaration {
            CoverImageContracts.require(capabilityId, "capabilityId");
            CoverImageContracts.require(contractVersion, "contractVersion");
        }
    }

    /**
     * Provider declaration: capability-independent provider identity + the capability list the
     * provider serves. Capability ids are unique within one provider and the list must not be empty.
     */
    record Manifest(
            String providerId,
            String providerImplementationId,
            String providerVersion,
            List<CapabilityDeclaration> capabilities,
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

        public Manifest {
            CoverImageContracts.require(providerId, "providerId");
            CoverImageContracts.require(providerImplementationId, "providerImplementationId");
            CoverImageContracts.require(providerVersion, "providerVersion");
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
            return capabilities.stream().anyMatch(declared -> declared.capabilityId().equals(capabilityId));
        }
    }

    record Result(byte[] bytes, String contentType, String failureCode) {

        public static Result success(byte[] bytes, String contentType) {
            return new Result(bytes, contentType, null);
        }

        public static Result failure(String failureCode) {
            return new Result(null, null, failureCode);
        }

        public boolean succeeded() {
            return bytes != null && failureCode == null;
        }
    }
}
