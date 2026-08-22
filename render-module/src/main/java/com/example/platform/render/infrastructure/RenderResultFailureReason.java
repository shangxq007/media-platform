package com.example.platform.render.infrastructure;

/**
 * Typed render execution failure reason (PRE-#21 final exactness).
 *
 * <p>Semantic failure identity for authoritative render execution results
 * produced by {@link RenderOrchestrator}. The typed reason is the semantic
 * authority; any human-readable detail on the result is explanation only.
 *
 * <p>Ownership: orchestration layer (render-module infrastructure). Provider
 * native failures are mapped by provider adapters and never become these
 * categories directly; API/transport mapping stays in the API layer.
 *
 * <p>Module-local by design — NOT a global error code, NOT a mega enum.
 */
public enum RenderResultFailureReason {

    /** A requested render extent exists but no achieved extent was proven by
     *  execution evidence (fail-closed: unproven != authoritative). */
    RENDER_EXTENT_UNPROVEN,

    /** A requested render extent exists and an achieved extent was reported,
     *  but the achieved extent does not semantically equal the requested
     *  extent (start, end, or frame rate mismatch). */
    RENDER_EXTENT_NOT_ACHIEVED,

    /** Font preflight rejected the job before execution. */
    FONT_PREFLIGHT_FAILED,

    /** One or more execution steps failed. */
    STEP_FAILED,

    /** Orchestration-level failure (unexpected error during execution). */
    ORCHESTRATION_ERROR
}
