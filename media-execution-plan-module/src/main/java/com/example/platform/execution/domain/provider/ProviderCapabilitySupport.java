package com.example.platform.execution.domain.provider;

import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.CapabilityImplementationId;
import com.example.platform.extension.domain.ContractVersionRange;
import java.util.Objects;
import java.util.Optional;

/**
 * One declared execution-feasibility support projection over upstream #16 authority.
 * The optional implementation pin participates only in immutable provider binding.
 */
public record ProviderCapabilitySupport(
        CapabilityId capabilityId,
        ContractVersionRange contractVersionRange,
        Optional<CapabilityImplementationId> capabilityImplementationPin) {

    public ProviderCapabilitySupport {
        Objects.requireNonNull(capabilityId, "capabilityId");
        Objects.requireNonNull(contractVersionRange, "contractVersionRange");
        Objects.requireNonNull(capabilityImplementationPin, "capabilityImplementationPin");
    }

    public static ProviderCapabilitySupport unpinned(
            CapabilityId capabilityId, ContractVersionRange contractVersionRange) {
        return new ProviderCapabilitySupport(capabilityId, contractVersionRange, Optional.empty());
    }

    public static ProviderCapabilitySupport pinned(
            CapabilityId capabilityId,
            ContractVersionRange contractVersionRange,
            CapabilityImplementationId implementationId) {
        return new ProviderCapabilitySupport(
                capabilityId, contractVersionRange, Optional.of(Objects.requireNonNull(implementationId)));
    }
}
