package com.example.platform.render.ir;

import java.util.Objects;

/**
 * Reference to a specific version of an asset.
 */
public record AssetVersionRef(String assetId, String versionId) {
    public AssetVersionRef {
        Objects.requireNonNull(assetId, "assetId must not be null");
        Objects.requireNonNull(versionId, "versionId must not be null");
    }
}
