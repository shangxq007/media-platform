package com.example.platform.providerplugin.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.sandbox.execution.ExecutionBackend;
import com.example.platform.sandbox.execution.TaskCapability;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The registry validates unique capability ownership; it owns no concrete provider backend. */
class RuntimeExecutionBackendsTest {

    private static ExecutionBackend backend(String id, TaskCapability... capabilities) {
        return new ExecutionBackend() {
            @Override public String backendId() { return id; }
            @Override public boolean supports(TaskCapability capability) {
                return List.of(capabilities).contains(capability);
            }
            @Override public com.example.platform.sandbox.execution.ExecutionResult execute(
                    com.example.platform.sandbox.execution.ExecutionRequest request) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void resolvesTheBackendThatSupportsTheCapability() {
        RuntimeExecutionBackends registry = new RuntimeExecutionBackends(List.of(
                backend("local-process", TaskCapability.PROBE),
                backend("test-media", TaskCapability.MEDIA_PIPELINE)));

        assertThat(registry.resolve(TaskCapability.PROBE).orElseThrow().backendId())
                .isEqualTo("local-process");
        assertThat(registry.resolve(TaskCapability.MEDIA_PIPELINE).orElseThrow().backendId())
                .isEqualTo("test-media");
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    void duplicateCapabilityOwnershipIsRejected() {
        assertThatThrownBy(() -> new RuntimeExecutionBackends(List.of(
                backend("a", TaskCapability.TRANSCODE),
                backend("b", TaskCapability.TRANSCODE))))
                .hasMessageContaining("Duplicate execution binding");
    }

    @Test
    void unknownCapabilityResolvesEmpty() {
        RuntimeExecutionBackends registry = new RuntimeExecutionBackends(List.of(
                backend("local-process", TaskCapability.PROBE)));
        assertThat(registry.resolve(TaskCapability.TRANSCODE)).isEmpty();
    }
}
