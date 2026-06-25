package com.example.platform.shared.events;

/**
 * Published when a comment is added to a review.
 */
public record ReviewCommentAddedEvent(
        String reviewId,
        String projectId,
        String targetType,
        String targetId,
        String commentId,
        String authorUserId,
        String entityRef) {}
