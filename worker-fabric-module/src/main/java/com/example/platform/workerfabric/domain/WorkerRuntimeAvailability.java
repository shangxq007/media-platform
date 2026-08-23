package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** Current runtime reachability without deleting its stable identity. */
public record WorkerRuntimeAvailability(
        WorkerRuntimeId workerRuntimeId,
        WorkerRuntimeIncarnationId incarnationId,
        AvailabilityState state) {

    public WorkerRuntimeAvailability {
        Objects.requireNonNull(workerRuntimeId, "workerRuntimeId");
        Objects.requireNonNull(incarnationId, "incarnationId");
        Objects.requireNonNull(state, "state");
    }

    public boolean isReachable() {
        return state == AvailabilityState.REACHABLE;
    }

    public boolean matchesCurrentIncarnation(
            WorkerRuntimeId candidateRuntimeId,
            WorkerRuntimeIncarnationId candidateIncarnationId) {
        return workerRuntimeId.equals(candidateRuntimeId) && incarnationId.equals(candidateIncarnationId);
    }
}
