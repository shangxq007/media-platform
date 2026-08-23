package com.example.platform.execution.domain.provider;

import java.util.Objects;

/** Immutable static metadata describing one provider implementation. */
public record ProviderDescriptor(
        ProviderId providerId,
        ProviderImplementationId providerImplementationId,
        ProviderVersion providerVersion,
        ProviderExecutionContractVersion providerExecutionContractVersion,
        ProviderCapabilityProfileVersionOrDigest providerCapabilityProfileReference) {

    public ProviderDescriptor {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(providerImplementationId, "providerImplementationId");
        Objects.requireNonNull(providerVersion, "providerVersion");
        Objects.requireNonNull(providerExecutionContractVersion, "providerExecutionContractVersion");
        Objects.requireNonNull(providerCapabilityProfileReference, "providerCapabilityProfileReference");
    }
}
