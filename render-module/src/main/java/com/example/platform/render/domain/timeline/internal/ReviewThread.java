package com.example.platform.render.domain.timeline.internal;

import java.time.Instant;

/**
 * A threaded discussion anchored to a specific entity in a review diff.
 */
public record ReviewThread(
        String threadId,
        String reviewId,
        EntityRef entityRef,
        String diffId,
        ThreadStatus status,
        Instant createdAt) {

    public enum ThreadStatus {
        OPEN, RESOLVED
    }

    public static ReviewThread create(String threadId, String reviewId,
                                        EntityRef entityRef, String diffId) {
        return new ReviewThread(threadId, reviewId, entityRef, diffId,
                ThreadStatus.OPEN, Instant.now());
    }
}
