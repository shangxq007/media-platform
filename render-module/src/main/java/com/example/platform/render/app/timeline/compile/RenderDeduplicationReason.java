package com.example.platform.render.app.timeline.compile;

/**
 * Reason for a render deduplication decision.
 *
 * <p>Internal only.</p>
 */
public enum RenderDeduplicationReason {

    /** No existing render found for this fingerprint. */
    NO_EXISTING_RENDER,

    /** Exact READY Product matches this fingerprint. */
    READY_PRODUCT_MATCH,

    /** In-progress render matches this fingerprint (reserved). */
    IN_PROGRESS_MATCH,

    /** Previous render failed — safe to retry. */
    FAILED_PREVIOUS_ATTEMPT,

    /** Fingerprint could not be generated. */
    FINGERPRINT_UNAVAILABLE,

    /** Unsupported execution mode. */
    UNSUPPORTED_MODE,

    /** Ambiguous match — multiple candidates found. */
    AMBIGUOUS_MATCH,

    /** Lookup error. */
    LOOKUP_ERROR
}
