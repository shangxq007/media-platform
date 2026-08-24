package com.example.platform.workerfabric.domain;

import com.example.platform.execution.taskgraph.ExecutableTaskId;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Active Native Pull lease containing references only, never copied Host/Worker/Reservation objects. */
public record TaskLease(
        LeaseId id,
        ExecutableTaskId executableTaskId,
        ExecutionAssignmentId executionAssignmentId,
        ExecutionAttemptId executionAttemptId,
        ExecutionOwnershipGeneration ownershipGeneration,
        WorkerRuntimeId workerRuntimeId,
        WorkerRuntimeIncarnationId workerRuntimeIncarnationId,
        Set<ReservationId> reservationIds,
        Instant expiresAt,
        Instant lastHeartbeatAt,
        LeaseRenewalContract renewalContract,
        LeaseFencingToken fencingToken) {

    public TaskLease {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(executableTaskId, "executableTaskId");
        Objects.requireNonNull(executionAssignmentId, "executionAssignmentId");
        Objects.requireNonNull(executionAttemptId, "executionAttemptId");
        Objects.requireNonNull(ownershipGeneration, "ownershipGeneration");
        Objects.requireNonNull(workerRuntimeId, "workerRuntimeId");
        Objects.requireNonNull(workerRuntimeIncarnationId, "workerRuntimeIncarnationId");
        Objects.requireNonNull(reservationIds, "reservationIds");
        if (reservationIds.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("reservationIds element");
        }
        reservationIds = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(
                reservationIds.stream()
                        .sorted(Comparator.comparing(ReservationId::value))
                        .toList()));
        if (reservationIds.isEmpty()) {
            throw new IllegalArgumentException("TaskLease requires a reservation");
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(lastHeartbeatAt, "lastHeartbeatAt");
        Objects.requireNonNull(renewalContract, "renewalContract");
        Objects.requireNonNull(fencingToken, "fencingToken");
        if (!expiresAt.isAfter(lastHeartbeatAt)) {
            throw new IllegalArgumentException("lease expiry must follow its last heartbeat");
        }
    }
}
