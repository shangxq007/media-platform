package com.example.platform.workerfabric.domain;

/** Technical dependency conformance status; unknown evidence always fails closed. */
public enum RuntimeDependencyMatchStatus {
    CAN_MATCH,
    CANNOT_MATCH,
    UNKNOWN_FAIL_CLOSED
}
