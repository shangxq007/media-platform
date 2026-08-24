package com.example.platform.workerfabric.domain;

import com.example.platform.execution.taskgraph.ExecutableTaskId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Recoverable projection of every authority established by ASSIGNMENT_GRANT_V1. */
public record AssignmentGrant(
        RequestWorkId requestWorkId,
        ExecutionAssignment assignment,
        List<Reservation> reservations,
        TaskLease lease,
        ExecutionAttempt attempt) implements AssignmentGrantReference {

    public AssignmentGrant {
        Objects.requireNonNull(requestWorkId, "requestWorkId");
        Objects.requireNonNull(assignment, "assignment");
        reservations = List.copyOf(Objects.requireNonNull(reservations, "reservations"));
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(attempt, "attempt");
        Set<ReservationId> reservationIds = reservations.stream()
                .map(Reservation::id)
                .collect(Collectors.toUnmodifiableSet());
        if (reservations.isEmpty()
                || reservationIds.size() != reservations.size()
                || !reservationIds.equals(assignment.reservationIds())
                || !reservationIds.equals(lease.reservationIds())) {
            throw new IllegalArgumentException(
                    "grant assignment, reservations, and lease must bind the same reservation ids");
        }
        if (!assignment.executableTaskId().equals(attempt.executableTaskId())
                || !assignment.executableTaskId().equals(lease.executableTaskId())
                || !assignment.executionAttemptId().equals(attempt.id())
                || !assignment.executionAttemptId().equals(lease.executionAttemptId())
                || !assignment.id().equals(lease.executionAssignmentId())
                || !assignment.ownershipGeneration().equals(attempt.ownershipGeneration())
                || !assignment.ownershipGeneration().equals(lease.ownershipGeneration())
                || !assignment.workerRuntimeId().equals(lease.workerRuntimeId())
                || !assignment.workerRuntimeIncarnationId().equals(
                        lease.workerRuntimeIncarnationId())
                || attempt.backend() != ExecutionBackend.NATIVE_PULL_WORKER
                || reservations.stream().anyMatch(reservation ->
                        !reservation.physicalHostId().equals(assignment.physicalHostId()))) {
            throw new IllegalArgumentException(
                    "grant authorities must bind one task, assignment, attempt, and generation");
        }
    }

    @Override
    public ExecutableTaskId executableTaskId() {
        return assignment.executableTaskId();
    }
}
