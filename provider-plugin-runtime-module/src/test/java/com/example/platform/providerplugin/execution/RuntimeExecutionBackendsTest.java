package com.example.platform.providerplugin.execution;

import com.example.platform.sandbox.execution.*;
import java.util.List;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import com.example.platform.sandbox.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RuntimeExecutionBackendsTest {
    @Test void realCallerLookupHasOneBindingAndRejectsDuplicates() {
        var registry = RuntimeExecutionBackends.create();
        assertThat(registry.resolve(TaskCapability.ASR).orElseThrow()).isInstanceOf(LocalProcessExecutionBackend.class);
        assertThat(registry.resolve(TaskCapability.MEDIA_PIPELINE).orElseThrow().backendId()).isEqualTo("bmf");
        assertThat(registry.resolve(TaskCapability.REINDEX)).isEmpty();
        assertThatThrownBy(() -> new RuntimeExecutionBackends(List.of(new LocalProcessExecutionBackend(), new LocalProcessExecutionBackend())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate execution binding");
    }
    @Test void bmfCannotManufactureSuccessOrExecuteWithoutPlatformIdentity() {
        var backend = RuntimeExecutionBackends.create().resolve(TaskCapability.MEDIA_PIPELINE).orElseThrow();
        var request = ExecutionRequest.of("job", "task", TaskCapability.MEDIA_PIPELINE, List.of(), 30);
        assertThat(backend.execute(request).success()).isFalse();
        assertThat(backend.execute(request).errorCode()).isEqualTo("RUNTIME_ADAPTER_UNSUPPORTED_PLAN");
        assertThat(backend.execute(ExecutionRequest.of("", "task", TaskCapability.MEDIA_PIPELINE, List.of(), 30)).errorCode())
                .isEqualTo("INVALID_EXECUTION_REQUEST");
        try {
            Thread.currentThread().interrupt();
            assertThat(backend.execute(request).errorCode()).isEqualTo("CANCELLED");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
    }
    @Test void existingLocalCallerWithoutAnExplicitWorkspaceStillFailsClosed() {
        var backend = RuntimeExecutionBackends.create().resolve(TaskCapability.ASR).orElseThrow();
        var result = backend.execute(ExecutionRequest.of("job", "task", TaskCapability.ASR, List.of("whisper"), 30));
        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("SANDBOX_POLICY_UNSATISFIABLE");
    }
    @Test void localExecutionPropagatesTheCanonicalSandboxResult() throws Exception {
        var workspace = Path.of("/tmp/ep05-unit-workspace");
        var request = new ExecutionRequest("job", "task", TaskCapability.PROBE, workspace.toString(), Map.of(), List.of("probe"), 10, "tenant", "project", Map.of());
        var sandbox = mock(SandboxExecutionResult.class);
        when(sandbox.stdout()).thenReturn(new BoundedCapture("verified-output".getBytes(), false));
        when(sandbox.stderr()).thenReturn(new BoundedCapture(new byte[0], false));
        when(sandbox.exitCode()).thenReturn(OptionalInt.of(0));
        when(sandbox.failure()).thenReturn(Optional.empty());
        try (var launcher = mockStatic(LocalSandboxProcess.class)) {
            launcher.when(() -> LocalSandboxProcess.execute(anyList(), eq(workspace), eq(workspace), anySet(), anyMap(), any(), anyLong(), any())).thenReturn(sandbox);
            var backend = RuntimeExecutionBackends.create().resolve(TaskCapability.PROBE).orElseThrow();
            assertThat(backend.execute(request).stdout()).isEqualTo("verified-output");
            assertThat(backend.execute(request).success()).isTrue();
            when(sandbox.failure()).thenReturn(Optional.of(SandboxFailure.of(SandboxFailureCode.SANDBOX_UNAVAILABLE, "Unavailable", java.util.Set.of())));
            assertThat(backend.execute(request).success()).isFalse();
            assertThat(backend.execute(request).errorCode()).isEqualTo("SANDBOX_UNAVAILABLE");
        }
    }
}
