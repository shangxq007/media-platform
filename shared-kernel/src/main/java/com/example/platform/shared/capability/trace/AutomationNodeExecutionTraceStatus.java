package com.example.platform.shared.capability.trace;

/**
 * Status of a single node execution trace.
 */
public enum AutomationNodeExecutionTraceStatus {
    SUCCEEDED,
    FAILED,
    VALIDATION_FAILED,
    NOT_IMPLEMENTED,
    SKIPPED,
    CANCELLED,
    TIMED_OUT,
    DRY_RUN_SUCCEEDED
}
