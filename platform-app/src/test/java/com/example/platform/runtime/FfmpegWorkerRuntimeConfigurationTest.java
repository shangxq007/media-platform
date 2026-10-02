package com.example.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.domain.ArtifactCommitService;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.execution.binding.BoundGraphInputStore;
import com.example.platform.runtime.mediatask.MediaTaskActivity;
import com.example.platform.runtime.mediatask.MediaTaskPublicationSettings;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.provider.StorageProvider;
import com.example.platform.workerfabric.domain.AtomicAssignmentGrantBoundary;
import com.example.platform.workerfabric.domain.CompletionAuthorityPort;
import com.example.platform.workerfabric.domain.NativePullAdmissionPort;
import com.example.platform.workerfabric.reuse.ArtifactMaterializerPort;
import com.example.platform.workerfabric.reuse.ArtifactOutputCommitOrchestrator;
import com.example.platform.workerfabric.reuse.ArtifactReuseIndexPort;
import com.example.platform.workerfabric.reuse.ArtifactReuseResolver;
import com.example.platform.workerfabric.reuse.FencedReuseCompletionOrchestrator;
import com.example.platform.workerfabric.reuse.OutputStagingArea;
import com.example.platform.workerfabric.reuse.Phase16RuntimeMetrics;
import com.example.platform.workerfabric.reuse.RuntimeClosedLoopOrchestrator;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import java.time.Clock;
import java.util.Map;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

/**
 * P2-5b-2b-1b: the worker-scoped runtime configuration assembles the closed loop, the admission port
 * and the media task activity from existing beans, loads only for the worker role, and fails closed
 * on an ambiguous provider set or a missing materializer.
 */
class FfmpegWorkerRuntimeConfigurationTest {

    @TempDir Path temp;

    @Test
    void workerProfileLoadsTheClosedLoopAdmissionAndActivityBeans() throws Exception {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RuntimeClosedLoopOrchestrator.class);
            assertThat(context).hasSingleBean(NativePullAdmissionPort.class);
            assertThat(context).hasSingleBean(MediaTaskActivity.class);
            assertThat(context).hasSingleBean(ArtifactReuseResolver.class);
            assertThat(context).hasSingleBean(OutputStagingArea.class);
            assertThat(context).hasSingleBean(ArtifactOutputCommitOrchestrator.class);
            assertThat(context).hasSingleBean(FencedReuseCompletionOrchestrator.class);
            assertThat(context).hasSingleBean(Phase16RuntimeMetrics.class);
            assertThat(context).hasBean("workerStorageProviders");
            assertThat(context).hasSingleBean(MediaTaskPublicationSettings.class);
        });
    }

    @Test
    void nonWorkerProfileLoadsNoRuntimeBeans() throws Exception {
        runner().withPropertyValues("platform.runtime.role=API").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(RuntimeClosedLoopOrchestrator.class);
            assertThat(context).doesNotHaveBean(NativePullAdmissionPort.class);
            assertThat(context).doesNotHaveBean(MediaTaskActivity.class);
            assertThat(context).doesNotHaveBean(ArtifactReuseResolver.class);
        });
    }

    @Test
    void duplicateStorageProviderIdsFailTheContextClosed() throws Exception {
        Path plugins = Files.createDirectory(temp.resolve("plugins"));
        runner()
                .withBean("firstProvider", StorageProvider.class, () -> provider("duplicate-provider"))
                .withBean("secondProvider", StorageProvider.class, () -> provider("duplicate-provider"))
                .withPropertyValues("platform.ffmpeg-worker.plugins-directory=" + plugins)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("duplicate storage provider id");
                });
    }

    @Test
    void missingArtifactMaterializerFailsClosed() throws Exception {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        FfmpegWorkerRuntimeConfiguration.class, RuntimeBindingMapConfiguration.class)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(ArtifactQueryService.class, () -> mock(ArtifactQueryService.class))
                .withBean(ArtifactCommitService.class, () -> mock(ArtifactCommitService.class))
                .withBean(ArtifactReuseIndexPort.class, () -> mock(ArtifactReuseIndexPort.class))
                .withBean(CompletionAuthorityPort.class, () -> mock(CompletionAuthorityPort.class))
                .withBean(AtomicAssignmentGrantBoundary.class,
                        () -> mock(AtomicAssignmentGrantBoundary.class))
                .withBean(BoundGraphInputStore.class, () -> mock(BoundGraphInputStore.class))
                .withBean(StorageProvider.class, () -> provider("test-provider"))
                .withPropertyValues(workerProperties(temp))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(org.springframework.beans.factory.NoSuchBeanDefinitionException.class);
                });
    }

    @Test
    void emptyProviderRuntimeBindingsFailTheContextClosed() throws Exception {
        Files.createDirectories(temp.resolve("workspace"));
        new ApplicationContextRunner()
                .withUserConfiguration(
                        FfmpegWorkerRuntimeConfiguration.class,
                        EmptyRuntimeBindingMapConfiguration.class)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(ArtifactQueryService.class, () -> mock(ArtifactQueryService.class))
                .withBean(ArtifactCommitService.class, () -> mock(ArtifactCommitService.class))
                .withBean(ArtifactReuseIndexPort.class, () -> mock(ArtifactReuseIndexPort.class))
                .withBean(CompletionAuthorityPort.class, () -> mock(CompletionAuthorityPort.class))
                .withBean(AtomicAssignmentGrantBoundary.class,
                        () -> mock(AtomicAssignmentGrantBoundary.class))
                .withBean(BoundGraphInputStore.class, () -> mock(BoundGraphInputStore.class))
                .withBean(ArtifactMaterializerPort.class, () -> mock(ArtifactMaterializerPort.class))
                .withBean(StorageProvider.class, () -> provider("test-provider"))
                .withPropertyValues(workerProperties(temp))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("at least one provider runtime binding");
                });
    }

    /** TEST-ONLY replacement for the plugin wiring: one runtime binding, no plugin JAR needed. */
    @TestConfiguration
    static class RuntimeBindingMapConfiguration {

        @Bean
        Map<ProviderBindingPin, ProviderNativeRuntimeBinding<?>> providerNativeRuntimeBindings() {
            return Map.of(
                    mock(ProviderBindingPin.class),
                    mock(ProviderNativeRuntimeBinding.class));
        }
    }

    @TestConfiguration
    static class EmptyRuntimeBindingMapConfiguration {

        @Bean
        Map<ProviderBindingPin, ProviderNativeRuntimeBinding<?>> providerNativeRuntimeBindings() {
            return Map.of();
        }
    }

    private ApplicationContextRunner runner() throws Exception {
        Files.createDirectories(temp.resolve("plugins"));
        Files.createDirectories(temp.resolve("workspace"));
        return new ApplicationContextRunner()
                .withUserConfiguration(
                        FfmpegWorkerRuntimeConfiguration.class, RuntimeBindingMapConfiguration.class)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(ArtifactQueryService.class, () -> mock(ArtifactQueryService.class))
                .withBean(ArtifactCommitService.class, () -> mock(ArtifactCommitService.class))
                .withBean(ArtifactReuseIndexPort.class, () -> mock(ArtifactReuseIndexPort.class))
                .withBean(CompletionAuthorityPort.class, () -> mock(CompletionAuthorityPort.class))
                .withBean(AtomicAssignmentGrantBoundary.class,
                        () -> mock(AtomicAssignmentGrantBoundary.class))
                .withBean(BoundGraphInputStore.class, () -> mock(BoundGraphInputStore.class))
                .withBean(ArtifactMaterializerPort.class, () -> mock(ArtifactMaterializerPort.class))
                .withBean(StorageProvider.class, () -> provider("test-provider"))
                .withPropertyValues(workerProperties(temp));
    }

    private static String[] workerProperties(Path temp) {
        return new String[] {
            "platform.runtime.role=WORKER",
            "platform.ffmpeg-worker.plugins-directory=" + temp.resolve("plugins"),
            "platform.ffmpeg-worker.workspace-root=" + temp.resolve("workspace"),
            "platform.ffmpeg-worker.staging-root=" + temp.resolve("staging"),
            "platform.ffmpeg-worker.sandbox.ffmpeg=/bin/true"
        };
    }

    private static StorageProvider provider(String providerId) {
        StorageProvider provider = mock(StorageProvider.class);
        when(provider.providerId()).thenReturn(new StorageProviderId(providerId));
        return provider;
    }
}
