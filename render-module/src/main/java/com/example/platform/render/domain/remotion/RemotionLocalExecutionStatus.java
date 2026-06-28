package com.example.platform.render.domain.remotion;

/**
 * Status of a Remotion local execution attempt.
 * Internal only.
 */
public enum RemotionLocalExecutionStatus {
    NOT_IMPLEMENTED,
    BLOCKED_BY_PREFLIGHT,
    BLOCKED_BY_POLICY,
    BLOCKED_BY_RUNTIME,
    BLOCKED_BY_SANDBOX,
    BLOCKED_BY_UNSAFE_COMMAND,
    REJECTED_UNSUPPORTED_DOCUMENT,
    FAILED_CLOSED
}
