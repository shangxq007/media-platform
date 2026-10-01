package com.example.platform.providerplugin;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersion;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.execution.domain.provider.ProviderExecutionContractSchemaVersion;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import com.example.platform.execution.domain.provider.ProviderId;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.execution.domain.provider.ProviderVersion;
import java.util.List;

/** Shared TEST-ONLY provider metadata values (real records, no mocks). */
final class ProviderCatalogTestFixture {

    private static final ProviderExecutionContractVersion CONTRACT_VERSION =
            ProviderExecutionContractVersion.of(1, 0);
    private static final ProviderCapabilityProfileVersionOrDigest PROFILE_REFERENCE =
            ProviderCapabilityProfileVersionOrDigest.version(ProviderCapabilityProfileVersion.of(1, 0));

    private ProviderCatalogTestFixture() {
    }

    static ProviderBindingPin pin(String provider) {
        return new ProviderBindingPin(
                ProviderId.of(provider),
                ProviderImplementationId.of(provider + ".native"),
                ProviderVersion.of("1.0.0"),
                CONTRACT_VERSION,
                PROFILE_REFERENCE,
                List.of());
    }

    static ProviderDescriptor descriptor(String provider) {
        return new ProviderDescriptor(
                ProviderId.of(provider),
                ProviderImplementationId.of(provider + ".native"),
                ProviderVersion.of("1.0.0"),
                CONTRACT_VERSION,
                PROFILE_REFERENCE);
    }

    static ProviderExecutionContract executionContract() {
        return new ProviderExecutionContract(
                ProviderExecutionContractSchemaVersion.of(1), CONTRACT_VERSION, List.of());
    }

    static ProviderCapabilityProfile capabilityProfile() {
        return new ProviderCapabilityProfile(PROFILE_REFERENCE, List.of());
    }
}
