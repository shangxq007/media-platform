package com.example.platform.shared.capability.execution;

/**
 * Status of a system action execution.
 */
public enum SystemActionExecutionStatus {
    SUCCEEDED,
    FAILED,
    VALIDATION_FAILED,
    NOT_IMPLEMENTED,
    TIMED_OUT,
    CANCELLED,
    DRY_RUN_SUCCEEDED
}
