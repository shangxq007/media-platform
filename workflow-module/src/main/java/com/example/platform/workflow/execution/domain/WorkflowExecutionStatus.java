package com.example.platform.workflow.execution.domain;

/**
 * Canonical execution lifecycle (UWE-ADR-004 / UWEV1-ARSF).
 *
 * <p>Execution status is the AGGREGATE lifecycle. Node-level observation is a
 * separate concern (deferred). No near-synonym states — this exact vocabulary is
 * frozen.</p>
 */
public enum WorkflowExecutionStatus {
    PENDING,
    RUNNING,
    WAITING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMED_OUT
}
