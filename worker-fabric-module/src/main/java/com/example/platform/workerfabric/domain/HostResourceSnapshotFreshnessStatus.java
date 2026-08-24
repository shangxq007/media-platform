package com.example.platform.workerfabric.domain;

/** Fail-closed runtime disposition of host resource evidence. */
public enum HostResourceSnapshotFreshnessStatus {
    FRESH,
    NO_ASSIGNMENT,
    REPROBE_REQUIRED,
    FAIL_CLOSED
}
