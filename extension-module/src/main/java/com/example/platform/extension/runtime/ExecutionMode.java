package com.example.platform.extension.runtime;

/**
 * Plugin Runtime V2 execution mode.
 *
 * <p>Frozen per PRV2-ADR-005. GPU is NOT an execution mode — GPU belongs to
 * {@link ResourceRequirements}.</p>
 */
public enum ExecutionMode {
    TRUSTED_IN_PROCESS,
    LOCAL_PROCESS,
    SANDBOX_PROCESS,
    REMOTE_WORKER,
    COMPOSITE
}
