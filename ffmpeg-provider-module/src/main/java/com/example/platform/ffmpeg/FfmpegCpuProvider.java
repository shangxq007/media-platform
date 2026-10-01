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
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint;
import com.example.platform.workerfabric.domain.RuntimeLifecycleKind;
import com.example.platform.workerfabric.domain.RuntimeSupportIdentifier;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportRequirement;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.capability.ContractVersion;
import com.example.platform.shared.capability.ContractVersionRange;
import java.util.List;

/**
 * Immutable identity and exact binding for the bounded CPU-only FFmpeg provider.
 *
 * <p>One runtime (the sandboxed native ffmpeg process) serves the capability-neutral
 * transcode slice plus the typed render-planning capabilities the provider can genuinely
 * lower: {@code video.decode}, {@code render.composite} and {@code render.output}.</p>
 */
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
    /** #20 render-planning decode capability. */
    public static final CapabilityId VIDEO_DECODE_CAPABILITY = CapabilityId.of("video.decode");
    /** #20 render-planning visual composition capability. */
    public static final CapabilityId COMPOSITE_CAPABILITY = CapabilityId.of("render.composite");
    /** #20 render-planning output encoding capability. */
    public static final CapabilityId OUTPUT_CAPABILITY = CapabilityId.of("render.output");
    public static final ContractVersionRange CAPABILITY_CONTRACT_RANGE =
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
                    List.of(
                            new ProviderCapabilityContractReference(
                                    TRANSCODE_CAPABILITY, CAPABILITY_CONTRACT_RANGE),
                            new ProviderCapabilityContractReference(
                                    VIDEO_DECODE_CAPABILITY, CAPABILITY_CONTRACT_RANGE),
                            new ProviderCapabilityContractReference(
                                    COMPOSITE_CAPABILITY, CAPABILITY_CONTRACT_RANGE),
                            new ProviderCapabilityContractReference(
                                    OUTPUT_CAPABILITY, CAPABILITY_CONTRACT_RANGE)));

    public static final ProviderCapabilityProfile CAPABILITY_PROFILE =
            new ProviderCapabilityProfile(
                    CAPABILITY_PROFILE_REFERENCE,
                    List.of(
                            ProviderCapabilitySupport.unpinned(
                                    TRANSCODE_CAPABILITY, CAPABILITY_CONTRACT_RANGE),
                            ProviderCapabilitySupport.unpinned(
                                    VIDEO_DECODE_CAPABILITY, CAPABILITY_CONTRACT_RANGE),
                            ProviderCapabilitySupport.unpinned(
                                    COMPOSITE_CAPABILITY, CAPABILITY_CONTRACT_RANGE),
                            ProviderCapabilitySupport.unpinned(
                                    OUTPUT_CAPABILITY, CAPABILITY_CONTRACT_RANGE)));

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

    /**
     * Stage-1 static compatibility declared by this provider.
     *
     * <p>Only genuinely served facts: pinned source inputs plus intermediate/final
     * outputs (the bounded slice's inter-task Artifact boundaries), the H.264 codec it
     * emits, CPU device class, a native child process executed under the sandbox,
     * SANDBOXED mode only (the binding factory fails closed when bubblewrap is
     * unavailable), deterministic output (bitexact + single-threaded), and supported
     * lowering. Mandatory-materialization markers and boundary contracts are
     * deliberately NOT declared: the bounded slice does not serve them, so a plan
     * requiring them fails closed.</p>
     */
    public static final ProviderStaticCompatibility STATIC_COMPATIBILITY =
            new ProviderStaticCompatibility(
                    ProviderStaticCompatibility.Knowledge.DECLARED,
                    List.of(
                            ProviderStaticCompatibility.ArtifactRequirementKind.PINNED_SOURCE_INPUT,
                            ProviderStaticCompatibility.ArtifactRequirementKind.INTERMEDIATE_OUTPUT,
                            ProviderStaticCompatibility.ArtifactRequirementKind.FINAL_OUTPUT),
                    List.of(StaticCompatibilityConstraint.CodecId.of("h264")),
                    List.of(StaticCompatibilityConstraint.ProviderDeviceKind.CPU),
                    List.of(StaticCompatibilityConstraint.ProviderRuntimeClass.NATIVE_PROCESS,
                            StaticCompatibilityConstraint.ProviderRuntimeClass.ISOLATED_PROCESS),
                    List.of(ProviderStaticCompatibility.SandboxMode.SANDBOXED),
                    List.of(ProviderStaticCompatibility.DeterminismClass.DETERMINISTIC),
                    List.of(),
                    ProviderStaticCompatibility.LoweringSupport.SUPPORTED);

    private FfmpegCpuProvider() {}
}
