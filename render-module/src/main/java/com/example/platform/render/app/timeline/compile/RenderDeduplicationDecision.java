package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.app.timeline.TimelineRevisionRenderService;

/**
 * Decision from render deduplication lookup.
 *
 * <p>Internal only — captures whether to proceed, reuse, or fail.</p>
 *
 * @param type        the decision type
 * @param reason      the reason for the decision
 * @param fingerprint the request fingerprint
 * @param reusedResult the existing result to reuse (null if proceeding with new render)
 * @param message     human-readable explanation
 */
public record RenderDeduplicationDecision(
        RenderDeduplicationDecisionType type,
        RenderDeduplicationReason reason,
        RenderRequestFingerprint fingerprint,
        TimelineRevisionRenderService.RevisionRenderResult reusedResult,
        String message) {

    /**
     * Returns true if the decision is to proceed with a new render.
     */
    public boolean shouldProceed() {
        return type == RenderDeduplicationDecisionType.PROCEED_NEW_RENDER
                || type == RenderDeduplicationDecisionType.RETRY_AFTER_FAILURE;
    }

    /**
     * Returns true if the decision is to reuse an existing result.
     */
    public boolean shouldReuse() {
        return type == RenderDeduplicationDecisionType.REUSE_READY_PRODUCT
                && reusedResult != null;
    }

    /**
     * Returns true if the lookup failed.
     */
    public boolean isFailed() {
        return type == RenderDeduplicationDecisionType.FAILED_CLOSED;
    }

    /**
     * Create a proceed decision.
     */
    public static RenderDeduplicationDecision proceed(
            RenderRequestFingerprint fingerprint, RenderDeduplicationReason reason, String message) {
        return new RenderDeduplicationDecision(
                RenderDeduplicationDecisionType.PROCEED_NEW_RENDER, reason, fingerprint, null, message);
    }

    /**
     * Create a reuse decision.
     */
    public static RenderDeduplicationDecision reuse(
            RenderRequestFingerprint fingerprint,
            TimelineRevisionRenderService.RevisionRenderResult existingResult,
            String message) {
        return new RenderDeduplicationDecision(
                RenderDeduplicationDecisionType.REUSE_READY_PRODUCT,
                RenderDeduplicationReason.READY_PRODUCT_MATCH,
                fingerprint, existingResult, message);
    }

    /**
     * Create a retry decision.
     */
    public static RenderDeduplicationDecision retry(
            RenderRequestFingerprint fingerprint, String message) {
        return new RenderDeduplicationDecision(
                RenderDeduplicationDecisionType.RETRY_AFTER_FAILURE,
                RenderDeduplicationReason.FAILED_PREVIOUS_ATTEMPT,
                fingerprint, null, message);
    }

    /**
     * Create a failed-closed decision.
     */
    public static RenderDeduplicationDecision failedClosed(
            RenderRequestFingerprint fingerprint, String message) {
        return new RenderDeduplicationDecision(
                RenderDeduplicationDecisionType.FAILED_CLOSED,
                RenderDeduplicationReason.LOOKUP_ERROR,
                fingerprint, null, message);
    }
}
