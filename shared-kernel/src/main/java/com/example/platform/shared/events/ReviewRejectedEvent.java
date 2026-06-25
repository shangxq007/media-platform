package com.example.platform.shared.events;

/**
 * Published when a review is rejected.
 */
public record ReviewRejectedEvent(
        String reviewId,
        String projectId,
        String targetType,
        String targetId) {}
