package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** Current host reachability without deleting its stable identity. */
public record PhysicalHostAvailability(
        PhysicalHostId physicalHostId,
        PhysicalHostIncarnationId incarnationId,
        AvailabilityState state) {

    public PhysicalHostAvailability {
        Objects.requireNonNull(physicalHostId, "physicalHostId");
        Objects.requireNonNull(incarnationId, "incarnationId");
        Objects.requireNonNull(state, "state");
    }

    public boolean isReachable() {
        return state == AvailabilityState.REACHABLE;
    }

    public boolean matchesCurrentIncarnation(
            PhysicalHostId candidateHostId,
            PhysicalHostIncarnationId candidateIncarnationId) {
        return physicalHostId.equals(candidateHostId) && incarnationId.equals(candidateIncarnationId);
    }
}
