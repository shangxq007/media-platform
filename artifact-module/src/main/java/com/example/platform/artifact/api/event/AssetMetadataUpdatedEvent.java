package com.example.platform.artifact.api.event;

/**
 * Published when asset metadata is updated (governance, version, or probe data).
 */
public record AssetMetadataUpdatedEvent(
        String assetId,
        String assetVersion,
        String assetType,
        String projectId) {}
