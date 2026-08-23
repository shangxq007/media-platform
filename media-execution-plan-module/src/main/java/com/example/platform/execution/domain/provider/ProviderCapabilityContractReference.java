package com.example.platform.execution.domain.provider;

import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.ContractVersionRange;
import java.util.Objects;

/**
 * Reference from a provider execution contract to an upstream #16 capability contract.
 * This type does not define capability identity, schema, or lifecycle.
 */
public record ProviderCapabilityContractReference(
        CapabilityId capabilityId,
        ContractVersionRange contractVersionRange) {

    public ProviderCapabilityContractReference {
        Objects.requireNonNull(capabilityId, "capabilityId");
        Objects.requireNonNull(contractVersionRange, "contractVersionRange");
    }
}
