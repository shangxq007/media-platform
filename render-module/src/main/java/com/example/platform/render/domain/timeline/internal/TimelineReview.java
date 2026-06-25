package com.example.platform.render.domain.timeline.internal;

import java.time.Instant;

/**
 * A review of a timeline revision, structured like a GitHub Pull Request.
 *
 * <p>Reviews are revision-scoped (not branch-scoped). Each review tracks status
 * from OPEN through APPROVED/CHANGES_REQUESTED to MERGED/CLOSED.</p>
 */
public record TimelineReview(
        String reviewId,
        String projectId,
        String tenantId,
        String revisionId,
        String authorUserId,
        String title,
        String description,
        ReviewStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public enum ReviewStatus {
        OPEN, APPROVED, CHANGES_REQUESTED, MERGED, CLOSED
    }

    public static TimelineReview create(String reviewId, String projectId, String tenantId,
                                          String revisionId, String authorUserId,
                                          String title, String description) {
        Instant now = Instant.now();
        return new TimelineReview(reviewId, projectId, tenantId, revisionId,
                authorUserId, title, description, ReviewStatus.OPEN, now, now);
    }
}
