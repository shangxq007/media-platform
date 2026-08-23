package com.example.platform.execution.compatibility;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import java.util.Objects;

/** Immutable provider implementation candidate; {@link ProviderBindingPin} is its sole binding authority. */
public record ProviderCandidate(
        ProviderBindingPin bindingPin,
        ProviderDescriptor descriptor,
        ProviderExecutionContract executionContract,
        ProviderCapabilityProfile capabilityProfile,
        ProviderStaticCompatibility staticCompatibility) {

    public ProviderCandidate {
        Objects.requireNonNull(bindingPin, "bindingPin");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(executionContract, "executionContract");
        Objects.requireNonNull(capabilityProfile, "capabilityProfile");
        Objects.requireNonNull(staticCompatibility, "staticCompatibility");
    }
}
