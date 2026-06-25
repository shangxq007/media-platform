package com.example.platform.shared.events;

/**
 * Published when a review is created for a timeline or asset.
 */
public record ReviewCreatedEvent(
        String reviewId,
        String projectId,
        String targetType,
        String targetId,
        String authorUserId,
        String title) {}
