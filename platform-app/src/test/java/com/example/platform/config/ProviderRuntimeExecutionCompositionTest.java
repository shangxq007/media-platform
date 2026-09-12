package com.example.platform.config;

import com.example.platform.extension.app.PluginRegistryImpl;
import com.example.platform.providerplugin.execution.RuntimeExecutionBackends;
import com.example.platform.render.infrastructure.asset.provider.WhisperAsrProvider;
import com.example.platform.sandbox.execution.ExecutionBackendRegistry;
import com.example.platform.sandbox.execution.TaskCapability;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ProviderRuntimeExecutionCompositionTest {
    @TempDir Path plugins;
    @Test void platformOwnsOneRuntimeCompositionAndActualWhisperCallerUsesIt() {
        new ApplicationContextRunner()
                .withUserConfiguration(ProviderPluginManagerConfiguration.class)
                .withPropertyValues("app.extensions.plugins-dir=" + plugins)
                .withBean(PluginRegistryImpl.class, () -> mock(PluginRegistryImpl.class))
                .withBean(WhisperAsrProvider.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ExecutionBackendRegistry.class);
                    var registry = context.getBean(ExecutionBackendRegistry.class);
                    assertThat(registry).isInstanceOf(RuntimeExecutionBackends.class);
                    assertThat(registry.resolve(TaskCapability.MEDIA_PIPELINE).orElseThrow().getClass().getPackageName())
                            .isEqualTo("com.example.platform.bmf");
                    assertThatThrownBy(() -> context.getBean(WhisperAsrProvider.class)
                            .transcribe("unused.wav", "base", "en", "job", "task"))
                            .isInstanceOf(IllegalStateException.class).hasMessageContaining("Explicit working directory");
                });
    }
}
