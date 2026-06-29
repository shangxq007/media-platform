package com.example.platform.render.domain.visual;

/**
 * Provider visual consistency level.
 * Immutable enum. Internal domain model.
 */
public enum VisualConsistencyLevel {
    /** Expected to match platform reference behavior closely. */
    EXACT,
    /** Expected to be visually close but provider differences are allowed. */
    APPROX,
    /** Only guaranteed under a specific provider. */
    PROVIDER_SPECIFIC,
    /** Provider does not support this capability. */
    UNSUPPORTED,
    /** Capability must not be used. */
    FORBIDDEN,
    /** Not evaluated yet. */
    UNKNOWN
}
