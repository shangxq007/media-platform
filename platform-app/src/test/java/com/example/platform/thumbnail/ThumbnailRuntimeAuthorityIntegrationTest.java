package com.example.platform.thumbnail;

import static org.assertj.core.api.Assertions.*;
import com.example.platform.sandbox.execution.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ThumbnailRuntimeAuthorityIntegrationTest {
    @Test void typedBackendPathCarriesPinnedProviderIdentity() {
        var backend = new ExecutionBackend() {
            @Override public String backendId() { return "test-worker-runtime"; }
            @Override public boolean supports(TaskCapability c) { return c == TaskCapability.THUMBNAIL; }
            @Override public ExecutionResult execute(ExecutionRequest r) {
                assertThat(r.taskCapability()).isEqualTo(TaskCapability.THUMBNAIL);
                assertThat(r.payload()).containsEntry("providerId", ThumbnailContracts.PROVIDER);
                return ExecutionResult.success(0, "worker-runtime-ok", "", 1);
            }
        };
        ExecutionBackendRegistry registry = new ExecutionBackendRegistry() {
            public Optional<ExecutionBackend> resolve(TaskCapability c) { return c == TaskCapability.THUMBNAIL ? Optional.of(backend) : Optional.empty(); }
            public int size() { return 1; }
        };
        var runtime = new WorkerRuntime(registry);
        var result = runtime.execute(ThumbnailContracts.PROVIDER, new ExecutionRequest("j", "t", TaskCapability.THUMBNAIL,
                ".", Map.of(), List.of(), 1, "tenant", "project", Map.of("providerId", ThumbnailContracts.PROVIDER)));
        assertThat(result.success()).isTrue();
        assertThatThrownBy(() -> runtime.execute("unregistered.provider", new ExecutionRequest("j", "t", TaskCapability.THUMBNAIL,
                ".", Map.of(), List.of(), 1, "tenant", "project", Map.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unregistered provider");
    }
}
