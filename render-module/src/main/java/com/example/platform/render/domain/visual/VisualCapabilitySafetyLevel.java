package com.example.platform.render.domain.visual;

/**
 * Safety classification for visual capabilities.
 * Immutable enum. Internal domain model.
 */
public enum VisualCapabilitySafetyLevel {
    /** Safe for production, validated parameters only. */
    SAFE,
    /** Requires parameter validation before use. */
    VALIDATED,
    /** Requires manual review or ADR before use. */
    RESTRICTED,
    /** Must not be used under any circumstance. */
    FORBIDDEN
}
