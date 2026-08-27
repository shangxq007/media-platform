package com.example.platform.ffmpeg;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderCapabilityContractReference;
import com.example.platform.execution.domain.provider.ProviderCapabilitySupport;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersion;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.execution.domain.provider.ProviderExecutionContractSchemaVersion;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import com.example.platform.execution.domain.provider.ProviderId;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.execution.domain.provider.ProviderVersion;
import com.example.platform.workerfabric.domain.RuntimeLifecycleKind;
import com.example.platform.workerfabric.domain.RuntimeSupportIdentifier;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportRequirement;
import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.ContractVersion;
import com.example.platform.extension.domain.ContractVersionRange;
import java.util.List;

/** Immutable identity and exact binding for the bounded CPU-only FFmpeg transcode provider. */
public final class FfmpegCpuProvider {

    public static final ProviderId PROVIDER_ID = ProviderId.of("ffmpeg");
    public static final ProviderImplementationId IMPLEMENTATION_ID =
            ProviderImplementationId.of("ffmpeg.cpu.native-pull.v1");
    public static final ProviderVersion VERSION = ProviderVersion.of("1.0.0");
    public static final ProviderExecutionContractVersion EXECUTION_CONTRACT_VERSION =
            ProviderExecutionContractVersion.of(1, 0);
    public static final ProviderCapabilityProfileVersionOrDigest CAPABILITY_PROFILE_REFERENCE =
            ProviderCapabilityProfileVersionOrDigest.version(
                    ProviderCapabilityProfileVersion.of(1, 0));
    public static final RuntimeSupportIdentifier RUNTIME_SUPPORT_IDENTIFIER =
            RuntimeSupportIdentifier.of("ffmpeg.cpu.transcode.v1");
    public static final CapabilityId TRANSCODE_CAPABILITY = CapabilityId.of("media.transcode");
    public static final ContractVersionRange TRANSCODE_CONTRACT_RANGE =
            ContractVersionRange.exactly(ContractVersion.of(1, 0));

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
                    List.of(new ProviderCapabilityContractReference(
                            TRANSCODE_CAPABILITY, TRANSCODE_CONTRACT_RANGE)));

    public static final ProviderCapabilityProfile CAPABILITY_PROFILE =
            new ProviderCapabilityProfile(
                    CAPABILITY_PROFILE_REFERENCE,
                    List.of(ProviderCapabilitySupport.unpinned(
                            TRANSCODE_CAPABILITY, TRANSCODE_CONTRACT_RANGE)));

    public static final ProviderBindingPin BINDING = new ProviderBindingPin(
            PROVIDER_ID,
            IMPLEMENTATION_ID,
            VERSION,
            EXECUTION_CONTRACT_VERSION,
            CAPABILITY_PROFILE_REFERENCE,
            List.of());

    public static final WorkerRuntimeSupportRequirement RUNTIME_SUPPORT_REQUIREMENT =
            new WorkerRuntimeSupportRequirement(
                    BINDING,
                    RuntimeLifecycleKind.EPHEMERAL_TASK,
                    RUNTIME_SUPPORT_IDENTIFIER);

    private FfmpegCpuProvider() {}
}
