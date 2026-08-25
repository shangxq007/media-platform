package com.example.platform.workerfabric.domain.providernative;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.execution.domain.provider.ProviderVersion;
import java.util.Objects;

/**
 * Immutable static provider metadata admitted before lowering.
 *
 * <p>No worker registry, device inventory, runtime availability, probe, scheduler, reservation,
 * heartbeat, filesystem discovery, network discovery, or mutable placement state is exposed here.
 */
public record StaticProviderExecutionContext(
        ProviderBindingPin providerBindingPin,
        ProviderImplementationId providerImplementationId,
        ProviderVersion providerVersion,
        ProviderExecutionContractVersion providerExecutionContractVersion,
        ProviderCapabilityProfileVersionOrDigest providerCapabilityProfileVersionOrDigest) {

    public StaticProviderExecutionContext {
        Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        Objects.requireNonNull(providerImplementationId, "providerImplementationId");
        Objects.requireNonNull(providerVersion, "providerVersion");
        Objects.requireNonNull(providerExecutionContractVersion, "providerExecutionContractVersion");
        Objects.requireNonNull(providerCapabilityProfileVersionOrDigest,
                "providerCapabilityProfileVersionOrDigest");
        if (!providerBindingPin.providerImplementationId().equals(providerImplementationId)
                || !providerBindingPin.providerVersion().equals(providerVersion)
                || !providerBindingPin.providerExecutionContractVersion().equals(
                        providerExecutionContractVersion)
                || !providerBindingPin.providerCapabilityProfileVersionOrDigest().equals(
                        providerCapabilityProfileVersionOrDigest)) {
            throw new ProviderNativeExecutionFailure(
                    ProviderNativeFailureCode.INVALID_STATIC_PROVIDER_EXECUTION_CONTRACT,
                    "static provider execution context must exactly match ProviderBindingPin");
        }
    }

    public static StaticProviderExecutionContext fromBinding(ProviderBindingPin bindingPin) {
        return new StaticProviderExecutionContext(
                bindingPin,
                bindingPin.providerImplementationId(),
                bindingPin.providerVersion(),
                bindingPin.providerExecutionContractVersion(),
                bindingPin.providerCapabilityProfileVersionOrDigest());
    }
}
