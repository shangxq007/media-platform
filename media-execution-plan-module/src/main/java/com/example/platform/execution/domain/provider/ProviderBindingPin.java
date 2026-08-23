package com.example.platform.execution.domain.provider;

import com.example.platform.extension.domain.CapabilityImplementationId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * The single immutable provider executable-binding authority for Roadmap #22.
 * Capability implementation pins are a distinct, canonically ordered set.
 */
public record ProviderBindingPin(
        ProviderId providerId,
        ProviderImplementationId providerImplementationId,
        ProviderVersion providerVersion,
        ProviderExecutionContractVersion providerExecutionContractVersion,
        ProviderCapabilityProfileVersionOrDigest providerCapabilityProfileVersionOrDigest,
        List<CapabilityImplementationId> capabilityImplementationPins) {

    public ProviderBindingPin {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(providerImplementationId, "providerImplementationId");
        Objects.requireNonNull(providerVersion, "providerVersion");
        Objects.requireNonNull(providerExecutionContractVersion, "providerExecutionContractVersion");
        Objects.requireNonNull(providerCapabilityProfileVersionOrDigest,
                "providerCapabilityProfileVersionOrDigest");
        Objects.requireNonNull(capabilityImplementationPins, "capabilityImplementationPins");

        var canonical = new ArrayList<CapabilityImplementationId>(capabilityImplementationPins.size());
        for (CapabilityImplementationId pin : capabilityImplementationPins) {
            canonical.add(Objects.requireNonNull(pin, "capabilityImplementationPins element"));
        }
        if (new HashSet<>(canonical).size() != canonical.size()) {
            throw new IllegalArgumentException("duplicate capability implementation pin: INVALID_PROVIDER_BINDING");
        }
        canonical.sort(Comparator.comparing(CapabilityImplementationId::value));
        capabilityImplementationPins = List.copyOf(canonical);
    }
}
