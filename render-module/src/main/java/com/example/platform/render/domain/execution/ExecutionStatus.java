package com.example.platform.render.domain.execution;

/**
 * Execution lifecycle states — platform-owned.
 * Environments report state; platform owns lifecycle semantics.
 */
public enum ExecutionStatus {
    CREATED,
    SUBMITTED,
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMED_OUT
}
