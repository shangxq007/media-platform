package com.example.platform.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.sandbox.SandboxCancellation;
import com.example.platform.providerplugin.ProviderPluginContribution;
import com.example.platform.providerplugin.ProviderPluginRuntimeContext;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FfmpegProviderPluginContributionTest {

    @TempDir Path temp;

    @Test
    void contribution_exposes_distinct_plugin_provider_and_runtime_contracts() {
        ProviderPluginContribution contribution = new FfmpegProviderPluginContribution();
        PluginDescriptor plugin = contribution.pluginDescriptor();

        assertThat(contribution.pluginId()).isEqualTo("media.transcode.ffmpeg");
        assertThat(contribution.pluginVersion()).isEqualTo("1.0.0");
        assertThat(plugin.pluginId()).isEqualTo(contribution.pluginId());
        assertThat(plugin.pluginVersion()).isEqualTo(contribution.pluginVersion());
        assertThat(plugin.handledObjects()).extracting(object -> object.objectTypeId())
                .containsExactly("ExecutableTask");
        assertThat(contribution.providerDescriptor()).isEqualTo(FfmpegCpuProvider.DESCRIPTOR);
        assertThat(contribution.providerExecutionContract())
                .isEqualTo(FfmpegCpuProvider.EXECUTION_CONTRACT);
        assertThat(contribution.providerCapabilityProfile())
                .isEqualTo(FfmpegCpuProvider.CAPABILITY_PROFILE);
        assertThat(contribution.workerRuntimeSupportRequirement())
                .isEqualTo(FfmpegCpuProvider.RUNTIME_SUPPORT_REQUIREMENT);
        assertThat(contribution.providerBindingPin()).isEqualTo(FfmpegCpuProvider.BINDING);
        assertThat(contribution.pluginId())
                .isNotEqualTo(FfmpegCpuProvider.PROVIDER_ID.value())
                .isNotEqualTo(FfmpegCpuProvider.IMPLEMENTATION_ID.value());
    }

    @Test
    void contribution_creates_only_the_existing_typed_provider_native_binding() {
        ProviderPluginContribution contribution = new FfmpegProviderPluginContribution();
        ProviderPluginRuntimeContext context = new ProviderPluginRuntimeContext(
                Path.of("/usr/bin/ffmpeg"), temp.resolve("workspace"),
                Duration.ofSeconds(10), 1024 * 1024, SandboxCancellation.never());

        assertThat(contribution.createRuntimeBinding(context)).isNotNull();
    }
}
