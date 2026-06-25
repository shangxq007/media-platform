package com.example.platform.render.domain.xmp;

import java.time.Instant;

/**
 * XMP {@code asset:*} namespace metadata.
 */
public record XmpAssetMetadata(
        String assetId,
        String assetType,
        String assetVersion,
        String ownerId,
        String projectId,
        String checksum,
        String storageUri,
        String entityRef,
        Instant createdAt,
        Instant updatedAt) {
}
