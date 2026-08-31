package com.example.platform.workerfabric.application;

import com.example.platform.workerfabric.domain.ExecutionAssignment;
import com.example.platform.workerfabric.domain.Reservation;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable durable assignment plus its exact reservation rows. */
public record AssignmentReadSnapshot(
        ExecutionAssignment assignment,
        Instant assignedAt,
        List<Reservation> reservations) {

    public AssignmentReadSnapshot {
        Objects.requireNonNull(assignment, "assignment");
        Objects.requireNonNull(assignedAt, "assignedAt");
        reservations = List.copyOf(Objects.requireNonNull(reservations, "reservations"));
        Set<?> reservationIds = reservations.stream()
                .map(Reservation::id)
                .collect(Collectors.toUnmodifiableSet());
        if (reservations.stream().anyMatch(Objects::isNull)
                || reservationIds.size() != reservations.size()
                || !reservationIds.equals(assignment.reservationIds())) {
            throw new IllegalArgumentException(
                    "assignment snapshot must contain its exact durable reservations");
        }
    }
}
