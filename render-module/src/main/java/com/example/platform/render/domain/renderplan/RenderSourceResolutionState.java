package com.example.platform.render.domain.renderplan;

/**
 * Per-render-node source resolution state (C4). Derived at planning time; NEVER
 * persisted into canonical authored state. CANONICAL_VALID != CURRENTLY_RENDERABLE.
 */
public enum RenderSourceResolutionState {
    /** Binding's immutable source semantics fully resolvable to concrete content. */
    RESOLVED,
    /** Resolution in progress (async probe/fetch). */
    PENDING,
    /** Resolution definitively failed (content missing / digest mismatch). */
    FAILED,
    /** Resolvable but blocked by a condition/dependency. */
    BLOCKED,
    /** Resource temporarily unavailable. */
    UNAVAILABLE
}
