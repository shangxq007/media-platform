package com.example.platform.shared.events;

/**
 * Published when an asset is archived.
 */
public record AssetArchivedEvent(
        String assetId,
        String assetType,
        String projectId) {}
