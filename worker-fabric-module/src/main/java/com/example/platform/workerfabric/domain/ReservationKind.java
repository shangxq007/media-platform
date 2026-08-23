package com.example.platform.workerfabric.domain;

/** Distinguishes ordinary task reservations from first-class resident runtime reservations. */
public enum ReservationKind {
    TASK,
    RESIDENT_RUNTIME
}
