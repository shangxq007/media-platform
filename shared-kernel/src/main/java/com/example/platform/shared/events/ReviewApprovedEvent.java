package com.example.platform.shared.events;

/**
 * Published when a review is approved.
 */
public record ReviewApprovedEvent(
        String reviewId,
        String projectId,
        String targetType,
        String targetId,
        String reviewerUserId) {}
