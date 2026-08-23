package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** One host-scoped commitment in the sole worker-fabric reservation authority. */
public record Reservation(
        ReservationId id,
        PhysicalHostId physicalHostId,
        ReservationKind kind,
        ReservedResources resources,
        ReservationState state) {

    public Reservation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(physicalHostId, "physicalHostId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(state, "state");
    }

    public boolean isResident() {
        return kind == ReservationKind.RESIDENT_RUNTIME;
    }

    public boolean keepsCapacityUnavailable() {
        return state != ReservationState.RELEASED;
    }
}
