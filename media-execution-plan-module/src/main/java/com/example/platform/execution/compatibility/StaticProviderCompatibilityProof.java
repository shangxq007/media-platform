package com.example.platform.execution.compatibility;

/**
 * Opaque evidence that the canonical {@link CompatibilityKernel} found one exact request and
 * provider candidate statically compatible.
 *
 * <p>The only permitted implementation has a private constructor owned by the kernel. Callers can
 * inspect and transport a proof, but cannot manufacture positive provenance.
 */
public sealed interface StaticProviderCompatibilityProof
        permits CompatibilityKernel.KernelProof {

    CompatibilityRequest compatibilityRequest();

    ProviderCandidate providerCandidate();

    default boolean proves(CompatibilityRequest request, ProviderCandidate candidate) {
        return compatibilityRequest().equals(request)
                && providerCandidate().equals(candidate);
    }
}
