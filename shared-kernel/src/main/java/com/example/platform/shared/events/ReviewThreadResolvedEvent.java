package com.example.platform.shared.events;

/**
 * Published when a review comment thread is resolved.
 */
public record ReviewThreadResolvedEvent(
        String reviewId,
        String projectId,
        String threadId,
        String entityRef) {}
