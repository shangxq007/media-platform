package com.example.platform.shared.events;

/**
 * Published when an asset is published to the platform library.
 */
public record AssetPublishedEvent(
        String assetId,
        String assetVersion,
        String assetType,
        String projectId,
        String publishStatus) {}
