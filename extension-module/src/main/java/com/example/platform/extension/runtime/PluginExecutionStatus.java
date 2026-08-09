package com.example.platform.extension.runtime;

/**
 * Terminal execution status of a plugin runtime execution.
 *
 * <p>Frozen per PRV2-ADR-011/016: TIMEOUT, CANCELLED and FAILURE are distinct
 * terminal states — they must never be collapsed into a generic failure.</p>
 */
public enum PluginExecutionStatus {
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    CANCELLED
}
