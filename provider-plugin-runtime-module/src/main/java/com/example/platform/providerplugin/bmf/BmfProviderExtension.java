package com.example.platform.providerplugin.bmf;

import com.example.platform.bmf.BmfCpuProvider;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.domain.provider.ProviderCapabilityContractReference;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderCapabilitySupport;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.execution.domain.provider.ProviderExecutionContractSchemaVersion;
import com.example.platform.extension.domain.CapabilityDescriptor;
import com.example.platform.extension.domain.HandledObjectDescriptor;
import com.example.platform.extension.domain.InvocationContract;
import com.example.platform.extension.domain.PermissionDescriptor;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.extension.domain.PluginGuarantee;
import com.example.platform.extension.domain.PluginRuntimeRequirement;
import com.example.platform.extension.domain.ResourceRequirement;
import com.example.platform.providerplugin.ProviderPluginContribution;
import com.example.platform.providerplugin.ProviderPluginRuntimeContext;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.capability.ContractVersion;
import com.example.platform.shared.capability.ContractVersionRange;
import com.example.platform.workerfabric.domain.ExecutionBackend;
import com.example.platform.workerfabric.domain.ProviderBackendExecutionSupport;
import com.example.platform.workerfabric.domain.ProviderHardwareRequirement;
import com.example.platform.workerfabric.domain.ProviderResourceProfile;
import com.example.platform.workerfabric.domain.RuntimeDependencyRequirement;
import com.example.platform.workerfabric.domain.RuntimeLifecycleKind;
import com.example.platform.workerfabric.domain.RuntimeSupportIdentifier;
import com.example.platform.workerfabric.domain.SandboxRuntimeRequirement;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportRequirement;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * BMF-PROVIDER-INTEGRATION-001 (Sprint 047, `docs/architecture/bmf-integration.md:100`): BMF
 * declared as a provider beside FFmpeg.
 *
 * <p>BMF is an embedded media framework with its own FFmpeg, so it is a provider on the same layer
 * as the FFmpeg contribution (N:M: several providers per capability, several capabilities per
 * provider) and is registered in-process rather than as a plugin JAR.
 *
 * <p>Honest declarations only: identity, descriptor, binding, static compatibility, hardware
 * requirements and runtime dependencies are the frozen {@link BmfCpuProvider} constants; the
 * capability profile and execution contract are declared here because those frozen constants carry
 * no support declarations; the runtime itself is not integrated yet, so
 * {@link #createRuntimeBinding} fails closed and {@code LoweringSupport.UNSUPPORTED} keeps the
 * Stage-1 kernel from ever scheduling BMF.
 */
public final class BmfProviderExtension implements ProviderPluginContribution {

    public static final String PLUGIN_ID = "media.bmf.cpu";
    public static final CapabilityId TRANSCODE_CAPABILITY = CapabilityId.of("media.transcode");
    public static final CapabilityId VIDEO_DECODE_CAPABILITY = CapabilityId.of("video.decode");
    public static final CapabilityId COMPOSITE_CAPABILITY = CapabilityId.of("render.composite");
    public static final CapabilityId OUTPUT_CAPABILITY = CapabilityId.of("render.output");
    public static final ContractVersionRange CAPABILITY_CONTRACT_RANGE =
            ContractVersionRange.exactly(ContractVersion.of(1, 0));

    private static final List<CapabilityId> CAPABILITIES = List.of(
            TRANSCODE_CAPABILITY,
            VIDEO_DECODE_CAPABILITY,
            COMPOSITE_CAPABILITY,
            OUTPUT_CAPABILITY);

    private static final ProviderCapabilityProfile CAPABILITY_PROFILE =
            new ProviderCapabilityProfile(
                    BmfCpuProvider.CAPABILITY_PROFILE_REFERENCE,
                    CAPABILITIES.stream()
                            .map(capability -> ProviderCapabilitySupport.unpinned(
                                    capability, CAPABILITY_CONTRACT_RANGE))
                            .toList());

    private static final ProviderExecutionContract EXECUTION_CONTRACT =
            new ProviderExecutionContract(
                    ProviderExecutionContractSchemaVersion.of(1),
                    BmfCpuProvider.EXECUTION_CONTRACT_VERSION,
                    CAPABILITIES.stream()
                            .map(capability -> new ProviderCapabilityContractReference(
                                    capability, CAPABILITY_CONTRACT_RANGE))
                            .toList());

    private static final WorkerRuntimeSupportRequirement RUNTIME_SUPPORT_REQUIREMENT =
            new WorkerRuntimeSupportRequirement(
                    BmfCpuProvider.BINDING,
                    RuntimeLifecycleKind.EPHEMERAL_TASK,
                    RuntimeSupportIdentifier.of("bmf.cpu.v1"));

    private static final PluginDescriptor PLUGIN_DESCRIPTOR = new PluginDescriptor(
            PLUGIN_ID,
            BmfCpuProvider.VERSION.value(),
            "1",
            "media-platform",
            List.of(new CapabilityDescriptor(
                    TRANSCODE_CAPABILITY.value(), "1.0", "transcode", "ExecutableTask",
                    "ProviderExecutionOutput", CapabilityDescriptor.InvocationMode.SYNC_ONLY)),
            List.of(new HandledObjectDescriptor(
                    "ExecutableTask", "1",
                    "com.example.platform.execution.taskgraph.ExecutableTask",
                    List.of("providerBindingPin"), List.of(),
                    HandledObjectDescriptor.TenantBehavior.TENANT_SCOPED)),
            InvocationContract.syncOnlyDefault(),
            List.of(
                    new PermissionDescriptor("bmf.cpu"),
                    new PermissionDescriptor("bmf.ffmpeg"),
                    new PermissionDescriptor("bmf.python")),
            bmfResourceRequirements(),
            PluginRuntimeRequirement.trustedInProcess(),
            PluginGuarantee.noneDeclared());

    @Override
    public String pluginId() {
        return PLUGIN_ID;
    }

    @Override
    public String pluginVersion() {
        return BmfCpuProvider.VERSION.value();
    }

    @Override
    public PluginDescriptor pluginDescriptor() {
        return PLUGIN_DESCRIPTOR;
    }

    @Override
    public ProviderDescriptor providerDescriptor() {
        return BmfCpuProvider.DESCRIPTOR;
    }

    @Override
    public ProviderExecutionContract providerExecutionContract() {
        return EXECUTION_CONTRACT;
    }

    @Override
    public ProviderCapabilityProfile providerCapabilityProfile() {
        return CAPABILITY_PROFILE;
    }

    @Override
    public ProviderStaticCompatibility providerStaticCompatibility() {
        return BmfCpuProvider.STATIC_COMPATIBILITY;
    }

    @Override
    public Optional<ProviderHardwareRequirement> providerHardwareRequirement() {
        return Optional.of(BmfCpuProvider.HARDWARE_REQUIREMENT);
    }

    @Override
    public List<RuntimeDependencyRequirement> runtimeDependencyRequirements() {
        return BmfCpuProvider.RUNTIME_DEPENDENCY_REQUIREMENTS;
    }

    @Override
    public SandboxRuntimeRequirement sandboxRequirement() {
        return SandboxRuntimeRequirement.REQUIRED;
    }

    @Override
    public Optional<ProviderResourceProfile> resourceProfile() {
        // No BMF runtime measurement exists; the demand derivation fails closed instead.
        return Optional.empty();
    }

    @Override
    public Optional<ProviderBackendExecutionSupport> providerBackendExecutionSupport() {
        return Optional.of(ProviderBackendExecutionSupport.declared(
                BmfCpuProvider.BINDING, Set.of(ExecutionBackend.NATIVE_PULL_WORKER)));
    }

    @Override
    public WorkerRuntimeSupportRequirement workerRuntimeSupportRequirement() {
        return RUNTIME_SUPPORT_REQUIREMENT;
    }

    @Override
    public ProviderBindingPin providerBindingPin() {
        return BmfCpuProvider.BINDING;
    }

    /** The BMF runtime is not integrated yet: declaring a provider does not fake an execution. */
    @Override
    public ProviderNativeRuntimeBinding<?> createRuntimeBinding(
            ProviderPluginRuntimeContext context) {
        throw new ProviderNativeExecutionFailure(
                ProviderNativeFailureCode.RUNTIME_ADAPTER_UNSUPPORTED_PLAN,
                "BMF runtime not yet integrated");
    }

    private static ResourceRequirement bmfResourceRequirements() {
        return new ResourceRequirement(
                1, 256, 50, 0,
                64L * 1024 * 1024, 64L * 1024 * 1024, 60_000L,
                false, 4096, false, 60_000L);
    }
}
