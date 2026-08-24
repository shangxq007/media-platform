package com.example.platform.workerfabric.domain;

/** Typed host-capacity disposition for fail-closed assignment and reconciliation. */
public enum SchedulableCapacityDisposition {
    AVAILABLE,
    NO_ASSIGNMENT,
    REPROBE_REQUIRED,
    RECONCILIATION_REQUIRED,
    FAIL_CLOSED
}
