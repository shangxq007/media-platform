package com.example.platform.workerfabric.domain;

/**
 * Bounded V1 reservation feasibility policy (owner decision Q2).
 *
 * <p>The bounded single-host configuration has no reservation-ledger view: the platform performs no
 * capacity-versus-reservation arithmetic for admission, so the signal is reported as
 * {@link ReservationFeasibility#UNKNOWN}. That is the fail-closed, honest value — the platform
 * claims no reservation it has not computed, and {@link RuntimeEligibilityEvaluator} decides what to
 * do with an unknown signal. It is an explicit policy value, not a placeholder and never a synthetic
 * {@code FEASIBLE}.
 *
 * <p>A real reservation view (static capacity minus active/recovery/resident reservations and safety
 * headroom) is future work; until then this mirror of {@link BoundedV1RuntimeAvailability} is the
 * single source of the bounded V1 value.
 */
public final class BoundedV1ReservationFeasibility {

    private BoundedV1ReservationFeasibility() {
    }

    /**
     * No reservation view exists in bounded V1: report {@code UNKNOWN} rather than assuming
     * feasibility.
     */
    public static ReservationFeasibility value() {
        return ReservationFeasibility.UNKNOWN;
    }
}
