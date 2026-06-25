package com.example.platform.render.domain.timeline.internal;

import java.time.Instant;

/**
 * A reviewer's decision on a timeline review.
 *
 * <p>Three decisions: APPROVE (allows merge), REQUEST_CHANGES (blocks merge),
 * REJECT (closes review without merge).</p>
 */
public record ReviewDecision(
        String decisionId,
        String reviewId,
        String reviewerUserId,
        Decision decision,
        Instant createdAt) {

    public enum Decision {
        APPROVE, REQUEST_CHANGES, REJECT
    }

    public static ReviewDecision approve(String decisionId, String reviewId, String reviewerId) {
        return new ReviewDecision(decisionId, reviewId, reviewerId, Decision.APPROVE, Instant.now());
    }

    public static ReviewDecision requestChanges(String decisionId, String reviewId, String reviewerId) {
        return new ReviewDecision(decisionId, reviewId, reviewerId, Decision.REQUEST_CHANGES, Instant.now());
    }

    public static ReviewDecision reject(String decisionId, String reviewId, String reviewerId) {
        return new ReviewDecision(decisionId, reviewId, reviewerId, Decision.REJECT, Instant.now());
    }
}
