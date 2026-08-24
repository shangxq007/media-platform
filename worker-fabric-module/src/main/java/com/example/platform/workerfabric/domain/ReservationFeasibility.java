package com.example.platform.workerfabric.domain;

/** Current reservation-ledger feasibility; observation cannot override this authority. */
public enum ReservationFeasibility {
    FEASIBLE,
    CONFLICT,
    UNKNOWN
}
