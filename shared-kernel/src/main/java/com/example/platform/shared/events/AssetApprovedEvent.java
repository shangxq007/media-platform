package com.example.platform.shared.events;

/**
 * Published when an asset review is approved.
 */
public record AssetApprovedEvent(
        String assetId,
        String projectId,
        String reviewId) {}
