package com.example.platform.workerfabric.domain;

/** Normalized backend state carried as evidence, never canonical task state. */
public enum ObservedExecutionState {
    SUBMITTED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    UNKNOWN
}
