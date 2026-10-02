package com.example.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.providerplugin.ProviderPluginCatalog;
import com.example.platform.providerplugin.ProviderPluginHost;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * P2-5b-2b-1a: the worker-scoped plugin wiring loads only for the worker role, exposes the plugin
 * host and catalog, derives the runtime binding map, and fails the context closed when the plugin
 * directory is unavailable.
 */
class FfmpegWorkerPluginConfigurationTest {

    @TempDir Path temp;

    @Test
    void workerProfileLoadsThePluginHostCatalogAndBindingMap() throws Exception {
        Path plugins = Files.createDirectory(temp.resolve("plugins"));

        runner(plugins).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ProviderPluginHost.class);
            assertThat(context).hasSingleBean(ProviderPluginCatalog.class);
            assertThat(context).hasBean("providerNativeRuntimeBindings");
            assertThat(bindings(context)).isEmpty();
            // The catalog bean is the host's own catalog, not a second instance.
            assertThat(context.getBean(ProviderPluginCatalog.class))
                    .isSameAs(context.getBean(ProviderPluginHost.class).catalog());
        });
    }

    @Test
    void workerProfileResolvesTheBoundingDefaultsWithoutExplicitKeys() throws Exception {
        Path plugins = Files.createDirectory(temp.resolve("plugins"));

        new ApplicationContextRunner()
                .withUserConfiguration(FfmpegWorkerPluginConfiguration.class)
                .withPropertyValues(
                        "platform.runtime.role=WORKER",
                        "platform.ffmpeg-worker.plugins-directory=" + plugins,
                        "platform.ffmpeg-worker.workspace-root=" + temp.resolve("workspace"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("providerNativeRuntimeBindings");
                });
    }

    @Test
    void nonWorkerProfileLoadsNoPluginBeans() throws Exception {
        Path plugins = Files.createDirectory(temp.resolve("plugins"));

        runner(plugins)
                .withPropertyValues("platform.runtime.role=API")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ProviderPluginHost.class);
                    assertThat(context).doesNotHaveBean(ProviderPluginCatalog.class);
                });
    }

    @Test
    void missingPluginDirectoryFailsTheWorkerContextClosed() {
        runner(temp.resolve("absent")).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .rootCause()
                    .hasMessageContaining("plugin directory is unavailable");
        });
    }

    private ApplicationContextRunner runner(Path pluginsDirectory) {
        return new ApplicationContextRunner()
                .withUserConfiguration(FfmpegWorkerPluginConfiguration.class)
                .withPropertyValues(
                        "platform.runtime.role=WORKER",
                        "platform.ffmpeg-worker.plugins-directory=" + pluginsDirectory,
                        "platform.ffmpeg-worker.workspace-root=" + temp.resolve("workspace"),
                        "platform.ffmpeg-worker.plugin-timeout=PT2M",
                        "platform.ffmpeg-worker.plugin-capture-bytes=4096",
                        "platform.ffmpeg-worker.sandbox.ffmpeg=/bin/true");
    }

    @SuppressWarnings("unchecked")
    private static Map<ProviderBindingPin, ProviderNativeRuntimeBinding<?>> bindings(
            org.springframework.context.ApplicationContext context) {
        return (Map<ProviderBindingPin, ProviderNativeRuntimeBinding<?>>)
                context.getBean("providerNativeRuntimeBindings");
    }
}
