package com.example.platform.product.domain;

import java.time.Instant;

/**
 * Asset library for managing workspace-level assets.
 * Provides organized asset management beyond project scope.
 */
public record AssetLibrary(
        String id,
        String workspaceId,
        String name,
        String description,
        AssetLibraryType type,
        int assetCount,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Create a new asset library.
     */
    public static AssetLibrary create(String id, String workspaceId, String name,
                                       String description, AssetLibraryType type) {
        Instant now = Instant.now();
        return new AssetLibrary(id, workspaceId, name, description, type, 0, now, now);
    }

    /**
     * Create the default library for a workspace.
     */
    public static AssetLibrary createDefault(String id, String workspaceId) {
        Instant now = Instant.now();
        return new AssetLibrary(id, workspaceId, "Default", "Default asset library",
                AssetLibraryType.GENERAL, 0, now, now);
    }

    /**
     * Increment asset count.
     */
    public AssetLibrary withAssetCount(int count) {
        return new AssetLibrary(id, workspaceId, name, description, type, count, createdAt, Instant.now());
    }

    /**
     * Update library details.
     */
    public AssetLibrary withDetails(String name, String description) {
        return new AssetLibrary(id, workspaceId, name, description, type, assetCount, createdAt, Instant.now());
    }
}
