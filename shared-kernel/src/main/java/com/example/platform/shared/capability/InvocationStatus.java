package com.example.platform.shared.capability;

/**
 * Status of an invocation or execution.
 */
public enum InvocationStatus {
    SUCCEEDED,
    FAILED,
    RETRYABLE_FAILED,
    TIMED_OUT,
    CANCELLED,
    PENDING,
    RUNNING
}
