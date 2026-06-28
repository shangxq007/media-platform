package com.example.platform.render.domain.remotion;

/**
 * Status of Remotion execution preflight check.
 *
 * <p>Internal only — Remotion-specific. Not a provider-neutral abstraction.
 * When a second non-FFmpeg provider is introduced, evaluate extracting
 * a shared provider-neutral preflight status/result model.</p>
 */
public enum RemotionExecutionPreflightStatus {
    BLOCKED_BY_POLICY,
    BLOCKED_BY_RUNTIME,
    BLOCKED_BY_SANDBOX,
    BLOCKED_BY_UNSUPPORTED_DOCUMENT,
    BLOCKED_BY_UNSAFE_COMMAND,
    /** All preflight checks passed, but execution remains disabled. */
    READY_BUT_EXECUTION_DISABLED,
    NOT_IMPLEMENTED
}
