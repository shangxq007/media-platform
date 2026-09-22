package com.example.platform.thumbnail;

import com.example.platform.sandbox.execution.*;
import java.util.Objects;

/** Worker runtime boundary: only registered execution backends may run provider commands. */
public final class WorkerRuntime {
    private final ExecutionBackendRegistry backends;
    public WorkerRuntime(ExecutionBackendRegistry backends) { this.backends = Objects.requireNonNull(backends); }
    public ExecutionResult execute(String providerId, ExecutionRequest request) {
        if (!ThumbnailContracts.PROVIDER.equals(providerId))
            throw new IllegalArgumentException("unregistered provider: " + providerId);
        var backend = backends.resolve(TaskCapability.THUMBNAIL)
                .orElseThrow(() -> new IllegalStateException("thumbnail execution backend is not registered"));
        return backend.execute(request);
    }
}
