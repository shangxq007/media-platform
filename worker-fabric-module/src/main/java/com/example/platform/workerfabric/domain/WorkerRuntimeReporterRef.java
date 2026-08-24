package com.example.platform.workerfabric.domain;

import java.util.Objects;

/**
 * Provenance for a local runtime that reported host evidence.
 *
 * <p>The reporter does not own the host capacity represented by the snapshot.
 */
public record WorkerRuntimeReporterRef(
        WorkerRuntimeId workerRuntimeId,
        WorkerRuntimeIncarnationId workerRuntimeIncarnationId,
        PhysicalHostId physicalHostId,
        PhysicalHostIncarnationId physicalHostIncarnationId) {

    public WorkerRuntimeReporterRef {
        Objects.requireNonNull(workerRuntimeId, "workerRuntimeId");
        Objects.requireNonNull(workerRuntimeIncarnationId, "workerRuntimeIncarnationId");
        Objects.requireNonNull(physicalHostId, "physicalHostId");
        Objects.requireNonNull(physicalHostIncarnationId, "physicalHostIncarnationId");
    }

    public static WorkerRuntimeReporterRef from(LocalWorkerRuntimeIncarnationBinding binding) {
        Objects.requireNonNull(binding, "binding");
        return new WorkerRuntimeReporterRef(
                binding.workerRuntimeId(),
                binding.workerRuntimeIncarnationId(),
                binding.physicalHostId(),
                binding.physicalHostIncarnationId());
    }
}
