package com.example.platform.render.domain.visual;

/**
 * Fallback behavior when a provider cannot fulfill a visual capability.
 * Immutable enum. Internal domain model.
 */
public enum VisualFallbackBehavior {
    /** No fallback available; capability is required. */
    NO_FALLBACK,
    /** Replace with a hard cut. */
    CUT,
    /** Fade out then fade in. */
    FADE_OUT_IN,
    /** Silently disable the effect. */
    DISABLE_EFFECT,
    /** Reject the entire render request. */
    REJECT_REQUEST,
    /** Require human review before proceeding. */
    MANUAL_REVIEW_REQUIRED,
    /** Only available under a specific provider; no cross-provider fallback. */
    PROVIDER_SPECIFIC_ONLY
}
