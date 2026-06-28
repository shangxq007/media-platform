package com.example.platform.render.app.timeline.compile;

/**
 * Decision type for render deduplication.
 *
 * <p>Internal only.</p>
 */
public enum RenderDeduplicationDecisionType {

    /** No existing render found — proceed with new render. */
    PROCEED_NEW_RENDER,

    /** Exact READY Product found — reuse it. */
    REUSE_READY_PRODUCT,

    /** In-progress render found — return existing job (reserved for future). */
    RETURN_IN_PROGRESS_JOB,

    /** Previous render failed — safe to retry. */
    RETRY_AFTER_FAILURE,

    /** Dedup lookup failed — fail closed. */
    FAILED_CLOSED
}
