package com.example.platform.ffmpeg;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.extension.domain.CapabilityDescriptor;
import com.example.platform.extension.domain.HandledObjectDescriptor;
import com.example.platform.extension.domain.InvocationContract;
import com.example.platform.extension.domain.PermissionDescriptor;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.extension.domain.PluginGuarantee;
import com.example.platform.extension.domain.PluginRuntimeRequirement;
import com.example.platform.extension.domain.ResourceRequirement;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportRequirement;
import com.example.platform.workerfabric.domain.ProviderResourceProfile;
import com.example.platform.workerfabric.domain.CpuArchitecture;
import com.example.platform.workerfabric.domain.ProviderHardwareRequirement;
import com.example.platform.workerfabric.domain.RuntimeDependencyRequirement;
import com.example.platform.workerfabric.domain.SandboxRuntimeRequirement;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding;
import com.example.platform.providerplugin.ProviderPluginContribution;
import com.example.platform.providerplugin.ProviderPluginRuntimeContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.pf4j.Extension;

/** Typed FFmpeg provider contribution discovered exclusively through PF4J. */
@Extension
public final class FfmpegProviderPluginContribution implements ProviderPluginContribution {

    public static final String PLUGIN_ID = "media.transcode.ffmpeg";
    public static final String PLUGIN_VERSION = "1.0.0";

    private static final PluginDescriptor PLUGIN_DESCRIPTOR = new PluginDescriptor(
            PLUGIN_ID,
            PLUGIN_VERSION,
            "1",
            "media-platform",
            List.of(new CapabilityDescriptor(
                    "media.transcode", "1.0", "transcode", "ExecutableTask",
                    "ProviderExecutionOutput", CapabilityDescriptor.InvocationMode.SYNC_ONLY)),
            List.of(new HandledObjectDescriptor(
                    "ExecutableTask", "1",
                    "com.example.platform.execution.taskgraph.ExecutableTask",
                    List.of("providerBindingPin"), List.of(),
                    HandledObjectDescriptor.TenantBehavior.TENANT_SCOPED)),
            InvocationContract.syncOnlyDefault(),
            List.of(
                    new PermissionDescriptor("ffmpeg.execute"),
                    new PermissionDescriptor("asset.read"),
                    new PermissionDescriptor("temporary-file.write")),
            ffmpegResourceRequirements(),
            PluginRuntimeRequirement.trustedInProcess(),
            ffmpegGuarantees());

    @Override
    public String pluginId() {
        return PLUGIN_ID;
    }

    @Override
    public String pluginVersion() {
        return PLUGIN_VERSION;
    }

    @Override
    public PluginDescriptor pluginDescriptor() {
        return PLUGIN_DESCRIPTOR;
    }

    @Override
    public ProviderDescriptor providerDescriptor() {
        return FfmpegCpuProvider.DESCRIPTOR;
    }

    @Override
    public ProviderExecutionContract providerExecutionContract() {
        return FfmpegCpuProvider.EXECUTION_CONTRACT;
    }

    @Override
    public ProviderCapabilityProfile providerCapabilityProfile() {
        return FfmpegCpuProvider.CAPABILITY_PROFILE;
    }

    @Override
    public WorkerRuntimeSupportRequirement workerRuntimeSupportRequirement() {
        return FfmpegCpuProvider.RUNTIME_SUPPORT_REQUIREMENT;
    }

    @Override
    public ProviderBindingPin providerBindingPin() {
        return FfmpegCpuProvider.BINDING;
    }

    @Override
    public ProviderStaticCompatibility providerStaticCompatibility() {
        return FfmpegCpuProvider.STATIC_COMPATIBILITY;
    }

    /**
     * Bounded V1 declared footprint for one FFmpeg software-transcode execution.
     *
     * <p>Owner-approved conservative values: two CPU cores (2000 millicores), 1 GiB memory and 2 GiB
     * temporary storage, with no device demands (the bounded host declares no GPU). Software x264
     * transcoding of the bounded source fragments runs well inside these bounds, so the declaration
     * does not overstate the footprint. This is the scheduling footprint — distinct from
     * {@link com.example.platform.workerfabric.domain.ProviderHardwareRequirement} (capability needs)
     * and from the plugin-descriptor {@code ResourceRequirement} (registry metadata).
     */
    @Override
    public Optional<ProviderResourceProfile> resourceProfile() {
        return Optional.of(new ProviderResourceProfile(
                2000L,
                1073741824L,
                2147483648L,
                Map.of()));
    }

    /**
     * Bounded V1 capability needs for the FFmpeg software runtime: x86-64, no device requirement, and
     * no extra build/codec features or sandbox permissions beyond the process sandbox itself.
     */
    @Override
    public Optional<ProviderHardwareRequirement> providerHardwareRequirement() {
        return Optional.of(new ProviderHardwareRequirement(
                FfmpegCpuProvider.IMPLEMENTATION_ID,
                CpuArchitecture.X86_64,
                Optional.empty(),
                List.of(),
                List.of(),
                List.of()));
    }

    /** FFmpeg links its codecs statically in the bounded image: no external runtime dependency. */
    @Override
    public List<RuntimeDependencyRequirement> runtimeDependencyRequirements() {
        return List.of();
    }

    /** The bounded ffmpeg execution runs inside the platform sandbox. */
    @Override
    public SandboxRuntimeRequirement sandboxRequirement() {
        return SandboxRuntimeRequirement.REQUIRED;
    }

    @Override
    public ProviderNativeRuntimeBinding<?> createRuntimeBinding(
            ProviderPluginRuntimeContext context) {
        return FfmpegCpuRuntimeBindingFactory.createRender(
                context.executable(),
                FfmpegSandboxWorkspace.under(context.workspaceRoot()),
                context.timeout(),
                context.captureBytes(),
                context.cancellation());
    }

    private static ResourceRequirement ffmpegResourceRequirements() {
        return new ResourceRequirement(
                1, 256, 50, 0,
                64L * 1024 * 1024, 64L * 1024 * 1024, 60_000L,
                false, 4096, false, 60_000L);
    }

    private static PluginGuarantee ffmpegGuarantees() {
        return PluginGuarantee.noneDeclared();
    }
}
