package com.example.platform.providerplugin.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.example.platform.sandbox.BoundedCapture;
import com.example.platform.sandbox.LocalSandboxProcess;
import com.example.platform.sandbox.SandboxExecutionResult;
import com.example.platform.sandbox.SandboxFailure;
import com.example.platform.sandbox.SandboxFailureCode;
import com.example.platform.sandbox.execution.ExecutionBackend;
import com.example.platform.sandbox.execution.ExecutionRequest;
import com.example.platform.sandbox.execution.TaskCapability;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** The registry is generic; LocalProcess keeps its canonical sandbox semantics. */
class RuntimeExecutionBackendsTest {

    private static ExecutionBackend backend(String id, TaskCapability... capabilities) {
        return new ExecutionBackend() {
            @Override public String backendId() { return id; }
            @Override public boolean supports(TaskCapability capability) {
                return List.of(capabilities).contains(capability);
            }
            @Override public com.example.platform.sandbox.execution.ExecutionResult execute(
                    ExecutionRequest request) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void localCallerLookupHasOneBindingAndRejectsDuplicates() {
        RuntimeExecutionBackends registry = new RuntimeExecutionBackends(List.of(new LocalProcessExecutionBackend()));
        assertThat(registry.resolve(TaskCapability.ASR).orElseThrow()).isInstanceOf(LocalProcessExecutionBackend.class);
        assertThat(registry.resolve(TaskCapability.REINDEX)).isEmpty();
        assertThatThrownBy(() -> new RuntimeExecutionBackends(
                List.of(new LocalProcessExecutionBackend(), new LocalProcessExecutionBackend())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate execution binding");
    }

    @Test
    void duplicateCapabilityOwnershipIsRejectedAcrossBackends() {
        assertThatThrownBy(() -> new RuntimeExecutionBackends(List.of(
                backend("a", TaskCapability.TRANSCODE),
                backend("b", TaskCapability.TRANSCODE))))
                .hasMessageContaining("Duplicate execution binding");
    }

    @Test
    void existingLocalCallerWithoutAnExplicitWorkspaceStillFailsClosed() {
        var backend = new LocalProcessExecutionBackend();
        var result = backend.execute(ExecutionRequest.of("job", "task", TaskCapability.ASR,
                List.of("whisper"), 30));
        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("SANDBOX_POLICY_UNSATISFIABLE");
    }

    @Test
    void localExecutionPropagatesTheCanonicalSandboxResult() throws Exception {
        var workspace = Path.of("/tmp/ep05-unit-workspace");
        var request = new ExecutionRequest("job", "task", TaskCapability.PROBE, workspace.toString(),
                Map.of(), List.of("probe"), 10, "tenant", "project", Map.of());
        var sandbox = mock(SandboxExecutionResult.class);
        when(sandbox.stdout()).thenReturn(new BoundedCapture("verified-output".getBytes(), false));
        when(sandbox.stderr()).thenReturn(new BoundedCapture(new byte[0], false));
        when(sandbox.exitCode()).thenReturn(OptionalInt.of(0));
        when(sandbox.failure()).thenReturn(Optional.empty());
        try (var launcher = mockStatic(LocalSandboxProcess.class)) {
            launcher.when(() -> LocalSandboxProcess.execute(anyList(), eq(workspace), eq(workspace),
                    anySet(), anyMap(), any(), anyLong(), any())).thenReturn(sandbox);
            var backend = new LocalProcessExecutionBackend();
            assertThat(backend.execute(request).stdout()).isEqualTo("verified-output");
            assertThat(backend.execute(request).success()).isTrue();
            when(sandbox.failure()).thenReturn(Optional.of(SandboxFailure.of(
                    SandboxFailureCode.SANDBOX_UNAVAILABLE, "Unavailable", java.util.Set.of())));
            assertThat(backend.execute(request).success()).isFalse();
            assertThat(backend.execute(request).errorCode()).isEqualTo("SANDBOX_UNAVAILABLE");
        }
    }
}
