package com.example.platform.bmfprovider;

import com.example.platform.bmf.BmfCpuProvider;
import com.example.platform.bmf.BmfCpuRuntimeBindingFactory;
import com.example.platform.bmf.BmfRuntimeBundleVerifier;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
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
import com.example.platform.workerfabric.domain.CpuArchitecture;
import com.example.platform.workerfabric.domain.PhysicalHostId;
import com.example.platform.workerfabric.domain.ProviderHardwareAvailableEvidence;
import com.example.platform.workerfabric.domain.ProviderHardwareObservation;
import com.example.platform.workerfabric.domain.RuntimeDependencyAbi;
import com.example.platform.workerfabric.domain.RuntimeDependencyCoordinate;
import com.example.platform.workerfabric.domain.RuntimeDependencyObservation;
import com.example.platform.workerfabric.domain.RuntimeDependencyObservedDependency;
import com.example.platform.workerfabric.domain.RuntimeDependencyProbeSchemaVersion;
import com.example.platform.workerfabric.domain.RuntimeDependencyVersion;
import com.example.platform.workerfabric.domain.WorkerRuntimeId;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.nio.file.Path;
import java.time.Instant;

/**
 * BMF-PROVIDER-INTEGRATION-001 (Sprint 047, `docs/architecture/bmf-integration.md:100`): BMF
 * declared as a provider beside FFmpeg.
 *
 * <p>BMF is an embedded media framework with its own FFmpeg, so it is a provider on the same layer
 * as the FFmpeg contribution (N:M: several providers per capability, several capabilities per
 * provider) and is registered in-process rather than as a plugin JAR.
 *
 * <p>Honest declarations only: identity, descriptor, binding, static compatibility, hardware
 * requirements and runtime dependencies are owned by {@link BmfCpuProvider}. The contribution publishes the capability
 * profile and execution contract derived from the native operations. The Worker form requires an exact verified runtime bundle.
 */
public final class BmfProviderExtension implements ProviderPluginContribution {

    public static final String PLUGIN_ID = "media.bmf.cpu";
    public static final CapabilityId TRANSCODE_CAPABILITY = CapabilityId.of("media.transcode");
    public static final CapabilityId VIDEO_DECODE_CAPABILITY = CapabilityId.of("video.decode");
    public static final CapabilityId COMPOSITE_CAPABILITY = CapabilityId.of("render.composite");
    public static final CapabilityId AUDIO_DECODE_CAPABILITY = CapabilityId.of("audio.decode");
    public static final CapabilityId MULTITRACK_CAPABILITY = CapabilityId.of("render.multitrack-composite");
    public static final CapabilityId OUTPUT_CAPABILITY = CapabilityId.of("render.output");
    public static final ContractVersionRange CAPABILITY_CONTRACT_RANGE =
            BmfCpuProvider.CAPABILITY_CONTRACT_RANGE;

    private final Path runtimeRoot;
    private final String bundleDigest;
    private final ProviderBindingPin binding;

    /** Declaration-only form used by API planning; a Worker supplies the pinned distribution root. */
    public BmfProviderExtension() {
        this(null, BmfCpuProvider.RUNTIME_BUNDLE_WITH_SNDIO_SHA256);
    }

    public BmfProviderExtension(Path runtimeRoot) {
        this(java.util.Objects.requireNonNull(runtimeRoot), BmfCpuProvider.RUNTIME_BUNDLE_WITH_SNDIO_SHA256);
    }

    public BmfProviderExtension(Path runtimeRoot, String bundleDigest) {
        this.runtimeRoot = runtimeRoot == null ? null : runtimeRoot.toAbsolutePath().normalize();
        this.bundleDigest = bundleDigest.isEmpty() ? BmfCpuProvider.RUNTIME_BUNDLE_WITH_SNDIO_SHA256 : bundleDigest;
        this.binding = BmfCpuProvider.bindingFor(this.bundleDigest);
    }

    /** Exact executable inside the distribution supplied to this Worker. */
    public Path runtimeExecutable() {
        if (runtimeRoot == null) {
            throw new IllegalStateException("BMF Worker runtime root is not configured");
        }
        return runtimeRoot.resolve("host-bundle/usr/bin/python3.9");
    }

    private static final PluginDescriptor PLUGIN_DESCRIPTOR = new PluginDescriptor(
            PLUGIN_ID,
            BmfCpuProvider.VERSION.value(),
            "1",
            "media-platform",
            List.of(
                    new CapabilityDescriptor(
                            TRANSCODE_CAPABILITY.value(), "1.0", "transcode", "ExecutableTask",
                            "ProviderExecutionOutput", CapabilityDescriptor.InvocationMode.SYNC_ONLY),
                    new CapabilityDescriptor(
                            VIDEO_DECODE_CAPABILITY.value(), "1.0", "decode", "ExecutableTask",
                            "ProviderExecutionOutput", CapabilityDescriptor.InvocationMode.SYNC_ONLY),
                    new CapabilityDescriptor(
                            COMPOSITE_CAPABILITY.value(), "1.0", "composite", "ExecutableTask",
                            "ProviderExecutionOutput", CapabilityDescriptor.InvocationMode.SYNC_ONLY),
                    new CapabilityDescriptor(
                            AUDIO_DECODE_CAPABILITY.value(), "1.0", "audio-decode", "ExecutableTask",
                            "ProviderExecutionOutput", CapabilityDescriptor.InvocationMode.SYNC_ONLY),
                    new CapabilityDescriptor(
                            MULTITRACK_CAPABILITY.value(), "1.0", "multitrack-composite", "ExecutableTask",
                            "ProviderExecutionOutput", CapabilityDescriptor.InvocationMode.SYNC_ONLY),
                    new CapabilityDescriptor(
                            OUTPUT_CAPABILITY.value(), "1.0", "encode", "ExecutableTask",
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
        return new ProviderDescriptor(binding.providerId(), binding.providerImplementationId(), binding.providerVersion(),
                binding.providerExecutionContractVersion(), binding.providerCapabilityProfileVersionOrDigest());
    }

    @Override
    public ProviderExecutionContract providerExecutionContract() {
        return BmfCpuProvider.EXECUTION_CONTRACT;
    }

    @Override
    public ProviderCapabilityProfile providerCapabilityProfile() {
        return new ProviderCapabilityProfile(
                    binding.providerCapabilityProfileVersionOrDigest(),
                BmfCpuProvider.SUPPORTS);
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
        return Optional.of(new ProviderResourceProfile(2000L, 1073741824L, 2147483648L,
                java.util.Map.of()));
    }

    @Override
    public Optional<ProviderBackendExecutionSupport> providerBackendExecutionSupport() {
        return Optional.of(ProviderBackendExecutionSupport.declared(
                binding, Set.of(ExecutionBackend.NATIVE_PULL_WORKER)));
    }

    @Override
    public WorkerRuntimeSupportRequirement workerRuntimeSupportRequirement() {
        return new WorkerRuntimeSupportRequirement(
                    binding,
                    RuntimeLifecycleKind.EPHEMERAL_TASK,
                    RuntimeSupportIdentifier.of("bmf.cpu.v1"));
    }

    @Override
    public ProviderBindingPin providerBindingPin() {
        return binding;
    }

    /** Provider-local evidence is emitted only for the verified, exact runtime distribution. */
    public Optional<RuntimeObservation> observeRuntime(WorkerRuntimeId runtimeId,
            PhysicalHostId hostId, CpuArchitecture architecture, Instant observedAt,
            Instant expiresAt) {
        if (runtimeRoot == null || architecture != CpuArchitecture.X86_64
                || !BmfRuntimeBundleVerifier.verify(runtimeRoot, bundleDigest)) {
            return Optional.empty();
        }
        var hardware = new ProviderHardwareObservation(BmfCpuProvider.IMPLEMENTATION_ID,
                runtimeId, hostId, Optional.empty(), observedAt, expiresAt,
                new ProviderHardwareAvailableEvidence(architecture,
                        List.of("bmf.cpu.enabled", "bmf.ffmpeg.enabled", "bmf.python.enabled"),
                        List.of(), List.of("ambient-module-authority.disabled"), Optional.empty()));
        var dependencies = new RuntimeDependencyObservation(BmfCpuProvider.IMPLEMENTATION_ID,
                runtimeId, Optional.empty(), RuntimeDependencyProbeSchemaVersion.CURRENT,
                observedAt, expiresAt, List.of(
                        dependency("bmf", "0.2.0", Optional.empty(),
                                List.of("ffmpeg.enabled", "python.enabled"),
                                List.of("ambient-module-authority.disabled", "cuda.disabled")),
                        dependency("ffmpeg", "4.3.7", Optional.of(RuntimeDependencyAbi.of("libavcodec.58")),
                                List.of(), List.of()),
                        dependency("numpy", "1.19.5", Optional.empty(), List.of(), List.of()),
                        dependency("python", "3.9.2", Optional.empty(), List.of(), List.of())));
        return Optional.of(new RuntimeObservation(hardware, dependencies));
    }

    public record RuntimeObservation(ProviderHardwareObservation hardware,
            RuntimeDependencyObservation dependencies) {}

    private static RuntimeDependencyObservedDependency dependency(String coordinate, String version,
            Optional<RuntimeDependencyAbi> abi, List<String> features, List<String> flags) {
        return new RuntimeDependencyObservedDependency(RuntimeDependencyCoordinate.of(coordinate),
                RuntimeDependencyVersion.of(version), abi, features, flags);
    }

    @Override
    public ProviderNativeRuntimeBinding<?> createRuntimeBinding(
            ProviderPluginRuntimeContext context) {
        if (runtimeRoot == null) {
            throw new ProviderNativeExecutionFailure(
                    ProviderNativeFailureCode.RUNTIME_ADAPTER_UNSUPPORTED_PLAN,
                    "BMF Worker runtime root is not configured");
        }
        if (!BmfRuntimeBundleVerifier.verify(runtimeRoot, bundleDigest)) {
            throw new ProviderNativeExecutionFailure(
                    ProviderNativeFailureCode.RUNTIME_ADAPTER_UNSUPPORTED_PLAN,
                    "BMF Worker runtime bundle failed verification");
        }
        if (!runtimeExecutable().equals(context.executable())) {
            throw new ProviderNativeExecutionFailure(
                    ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH,
                    "BMF Python must be the executable in the verified runtime bundle");
        }
        return BmfCpuRuntimeBindingFactory.create(context.executable(), runtimeRoot,
                context.workspaceRoot(), context.timeout(), context.captureBytes(),
                context.cancellation());
    }

    private static ResourceRequirement bmfResourceRequirements() {
        return new ResourceRequirement(
                1, 256, 50, 0,
                64L * 1024 * 1024, 64L * 1024 * 1024, 60_000L,
                false, 4096, false, 60_000L);
    }
}
