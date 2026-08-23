package com.example.platform.workerfabric.domain;

import java.util.Objects;
import java.util.Optional;

/** Immutable description of a local or remote executable runtime endpoint. */
public record WorkerRuntimeDescriptor(
        WorkerRuntimeId id,
        RuntimeLifecycleKind lifecycleKind,
        Optional<PhysicalHostId> physicalHostId) {

    public WorkerRuntimeDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(lifecycleKind, "lifecycleKind");
        physicalHostId = Objects.requireNonNull(physicalHostId, "physicalHostId");

        if (lifecycleKind == RuntimeLifecycleKind.REMOTE_RUNTIME && physicalHostId.isPresent()) {
            throw new IllegalArgumentException("REMOTE_RUNTIME must not identify a physical host");
        }
        if (lifecycleKind != RuntimeLifecycleKind.REMOTE_RUNTIME && physicalHostId.isEmpty()) {
            throw new IllegalArgumentException("local runtime must identify a physical host");
        }
    }

    public static WorkerRuntimeDescriptor local(
            WorkerRuntimeId id,
            RuntimeLifecycleKind lifecycleKind,
            PhysicalHostId physicalHostId) {
        if (lifecycleKind == RuntimeLifecycleKind.REMOTE_RUNTIME) {
            throw new IllegalArgumentException("local runtime cannot use REMOTE_RUNTIME lifecycle");
        }
        return new WorkerRuntimeDescriptor(id, lifecycleKind, Optional.of(physicalHostId));
    }

    public static WorkerRuntimeDescriptor remote(WorkerRuntimeId id) {
        return new WorkerRuntimeDescriptor(id, RuntimeLifecycleKind.REMOTE_RUNTIME, Optional.empty());
    }
}
