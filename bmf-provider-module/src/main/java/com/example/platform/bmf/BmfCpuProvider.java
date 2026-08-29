package com.example.platform.bmf;

import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
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

/** Immutable identity and fail-closed static declarations for the bounded BMF CPU provider. */
public final class BmfCpuProvider {

    public static final ProviderId PROVIDER_ID = ProviderId.of("bmf");
    public static final ProviderImplementationId IMPLEMENTATION_ID =
            ProviderImplementationId.of("bmf.cpu.v1");
    public static final ProviderVersion VERSION = ProviderVersion.of("1.0.0");
    public static final ProviderExecutionContractVersion EXECUTION_CONTRACT_VERSION =
            ProviderExecutionContractVersion.of(1, 0);
    public static final ProviderCapabilityProfileVersionOrDigest CAPABILITY_PROFILE_REFERENCE =
            ProviderCapabilityProfileVersionOrDigest.version(
                    ProviderCapabilityProfileVersion.of(1, 0));

    public static final ProviderDescriptor DESCRIPTOR = new ProviderDescriptor(
            PROVIDER_ID,
            IMPLEMENTATION_ID,
            VERSION,
            EXECUTION_CONTRACT_VERSION,
            CAPABILITY_PROFILE_REFERENCE);

    public static final ProviderExecutionContract EXECUTION_CONTRACT =
            new ProviderExecutionContract(
                    ProviderExecutionContractSchemaVersion.of(1),
                    EXECUTION_CONTRACT_VERSION,
                    List.of());

    public static final ProviderCapabilityProfile CAPABILITY_PROFILE =
            new ProviderCapabilityProfile(CAPABILITY_PROFILE_REFERENCE, List.of());

    public static final ProviderBindingPin BINDING = new ProviderBindingPin(
            PROVIDER_ID,
            IMPLEMENTATION_ID,
            VERSION,
            EXECUTION_CONTRACT_VERSION,
            CAPABILITY_PROFILE_REFERENCE,
            List.of());

    public static final ProviderStaticCompatibility STATIC_COMPATIBILITY =
            new ProviderStaticCompatibility(
                    ProviderStaticCompatibility.Knowledge.DECLARED,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    ProviderStaticCompatibility.LoweringSupport.UNSUPPORTED);

    private BmfCpuProvider() {}
}
