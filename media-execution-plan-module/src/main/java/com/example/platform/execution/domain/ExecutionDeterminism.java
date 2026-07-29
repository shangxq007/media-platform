package com.example.platform.execution.domain;

/**
 * Determinism classification for an execution step.
 *
 * <p>Closed, version-governed enum — serialized by name for canonical representation.
 * The determinism classification affects cache key eligibility:
 * DETERMINISTIC steps are cacheable; NON_DETERMINISTIC steps are not.
 */
public enum ExecutionDeterminism {
    /**
     * Step always produces identical output for identical inputs.
     */
    DETERMINISTIC,
    /**
     * Step produces identical output for identical inputs when given the same environment/config.
     */
    CONDITIONALLY_DETERMINISTIC,
    /**
     * Step may produce different output for identical inputs (e.g., LLM generation, real-time capture).
     */
    NON_DETERMINISTIC,
    /**
     * Determinism not yet classified.
     */
    UNKNOWN
}
