package com.example.platform.shared.events;

/**
 * Published when a new asset is registered via the Asset Registry.
 */
public record AssetRegisteredEvent(
        String assetId,
        String assetVersion,
        String assetType,
        String projectId,
        String tenantId,
        String storageUri) {}
