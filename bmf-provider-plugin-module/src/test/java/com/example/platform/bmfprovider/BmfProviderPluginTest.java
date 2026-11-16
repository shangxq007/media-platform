package com.example.platform.bmfprovider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.providerplugin.execution.RuntimeExecutionBackends;
import com.example.platform.sandbox.execution.ExecutionRequest;
import com.example.platform.sandbox.execution.TaskCapability;
import java.util.List;
import org.junit.jupiter.api.Test;

/** EP05: the BMF backend reached through the bridge still fails closed and honours cancellation. */
class BmfProviderPluginTest {

    @Test
    void bridgeContributionIsTheTypedProviderContribution() {
        assertThat(BmfProviderPlugin.contribution())
                .isInstanceOf(com.example.platform.providerplugin.ProviderPluginContribution.class)
                .isInstanceOf(BmfProviderExtension.class);
    }

    @Test
    void bmfCannotManufactureSuccessOrExecuteWithoutPlatformIdentity() {
        var backend = BmfProviderPlugin.executionBackend();
        var request = ExecutionRequest.of("job", "task", TaskCapability.MEDIA_PIPELINE, List.of(), 30);
        assertThat(backend.execute(request).success()).isFalse();
        assertThat(backend.execute(request).errorCode()).isEqualTo("RUNTIME_ADAPTER_UNSUPPORTED_PLAN");
        assertThat(backend.execute(ExecutionRequest.of("", "task", TaskCapability.MEDIA_PIPELINE, List.of(), 30))
                .errorCode()).isEqualTo("INVALID_EXECUTION_REQUEST");
        try {
            Thread.currentThread().interrupt();
            assertThat(backend.execute(request).errorCode()).isEqualTo("CANCELLED");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void registryResolvesTheBmfBackendOnlyWhenItIsContributed() {
        var registry = new RuntimeExecutionBackends(List.of(BmfProviderPlugin.executionBackend()));
        assertThat(registry.resolve(TaskCapability.MEDIA_PIPELINE).orElseThrow().backendId()).isEqualTo("bmf");
        assertThat(registry.resolve(TaskCapability.PROBE)).isEmpty();
        assertThatThrownBy(() -> new RuntimeExecutionBackends(List.of(
                BmfProviderPlugin.executionBackend(), BmfProviderPlugin.executionBackend())))
                .hasMessageContaining("Duplicate execution binding");
    }
}
