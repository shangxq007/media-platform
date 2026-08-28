package com.example.platform.ffmpeg;

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
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportRequirement;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding;
import com.example.platform.providerplugin.ProviderPluginContribution;
import com.example.platform.providerplugin.ProviderPluginRuntimeContext;
import java.util.List;
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
            ResourceRequirement.ffmpegDefaults(),
            PluginRuntimeRequirement.trustedInProcess(),
            PluginGuarantee.ffmpegDefaults());

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
    public ProviderNativeRuntimeBinding<?> createRuntimeBinding(
            ProviderPluginRuntimeContext context) {
        return FfmpegCpuRuntimeBindingFactory.create(
                context.executable(),
                FfmpegSandboxWorkspace.under(context.workspaceRoot()),
                context.timeout(),
                context.captureBytes(),
                context.cancellation());
    }
}
