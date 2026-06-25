package com.example.platform.shared.events;

/**
 * Published when a reviewer requests changes.
 */
public record ReviewChangesRequestedEvent(
        String reviewId,
        String projectId,
        String targetType,
        String targetId,
        String reviewerUserId) {}
