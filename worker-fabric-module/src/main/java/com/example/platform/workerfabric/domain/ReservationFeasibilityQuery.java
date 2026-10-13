package com.example.platform.workerfabric.domain;

/**
 * P1-1A.5: read-only, lock-free pre-admission reservation-feasibility query.
 *
 * <p><b>Pre-admission filter only.</b> The result is a conservative candidate filter: it never
 * authorizes, never writes a reservation, never takes a lock, and never replaces the final
 * in-transaction reservation check performed by
 * {@code JooqAtomicAssignmentGrantBoundary.tryGrant(...)}. A {@link ReservationFeasibility#FEASIBLE}
 * result only permits the prospect to continue to the repository-internal final Grant decision, which
 * re-evaluates the exact reservation constraints under the grant transaction and may still reject the
 * request with {@code GRANT_CONFLICT}.
 *
 * <p><b>Fail closed.</b> {@link ReservationFeasibility#UNKNOWN} is returned whenever the current
 * durable evidence cannot reliably prove or disprove feasibility: a missing or generation-mismatched
 * host resource snapshot (or snapshot-generation authority), a missing required device capacity row,
 * incomplete/invalid data, or a read failure. Absent data is never treated as zero demand, unlimited
 * capacity, no reservation, or {@code FEASIBLE}.
 */
public interface ReservationFeasibilityQuery {

    /**
     * Assesses reservation feasibility for one physical host incarnation + snapshot generation and one
     * resource demand.
     *
     * @return {@link ReservationFeasibility#FEASIBLE} when the current durable evidence proves the
     *     demand fits {@code capacity - safety_headroom - reserved(ACTIVE|RECOVERY_HOLD)} for every
     *     required resource and device; {@link ReservationFeasibility#CONFLICT} when the evidence is
     *     complete and the arithmetic proves the demand does not fit; {@link
     *     ReservationFeasibility#UNKNOWN} when the evidence cannot reliably decide.
     */
    ReservationFeasibility assess(
            PhysicalHostId physicalHostId,
            PhysicalHostIncarnationId physicalHostIncarnationId,
            HostResourceSnapshotGeneration snapshotGeneration,
            RuntimeResourceDemand demand);
}
