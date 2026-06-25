package com.example.platform.shared.events;

/**
 * Published when an asset is submitted for review.
 */
public record AssetSubmittedForReviewEvent(
        String assetId,
        String projectId,
        String reviewId) {}
