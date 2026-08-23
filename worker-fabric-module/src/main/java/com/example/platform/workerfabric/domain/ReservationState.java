package com.example.platform.workerfabric.domain;

/** Frozen reservation lifecycle states for Phase 3. */
public enum ReservationState {
    ACTIVE,
    /** Ownership is lost, release is unconfirmed, capacity is unavailable, reconciliation is required. */
    RECOVERY_HOLD,
    RELEASED
}
