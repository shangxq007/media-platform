package com.example.platform.workerfabric.domain;

import java.util.Objects;

/**
 * Current local-runtime relation to the exact physical-host incarnation that owns it.
 *
 * <p>This is runtime relationship state. It does not identify a provider or apply to remote
 * runtimes.
 */
public record LocalWorkerRuntimeIncarnationBinding(
        WorkerRuntimeId workerRuntimeId,
        WorkerRuntimeIncarnationId workerRuntimeIncarnationId,
        PhysicalHostId physicalHostId,
        PhysicalHostIncarnationId physicalHostIncarnationId) {

    public LocalWorkerRuntimeIncarnationBinding {
        Objects.requireNonNull(workerRuntimeId, "workerRuntimeId");
        Objects.requireNonNull(workerRuntimeIncarnationId, "workerRuntimeIncarnationId");
        Objects.requireNonNull(physicalHostId, "physicalHostId");
        Objects.requireNonNull(physicalHostIncarnationId, "physicalHostIncarnationId");
    }
}
