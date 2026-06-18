package com.example.platform.shared.capability.trace;

/**
 * Status of an automation execution trace.
 */
public enum AutomationExecutionTraceStatus {
    SUCCEEDED,
    PARTIALLY_SUCCEEDED,
    VALIDATION_FAILED,
    FAILED,
    CANCELLED,
    TIMED_OUT,
    DRY_RUN_SUCCEEDED,
    DRY_RUN_PARTIALLY_SUPPORTED
}
