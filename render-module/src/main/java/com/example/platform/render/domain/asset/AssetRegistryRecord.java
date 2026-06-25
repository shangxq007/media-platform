package com.example.platform.render.domain.asset;

import java.time.Instant;

/**
 * Phase 1 lightweight Asset Registry view.
 *
 * <p>Aggregates asset identity, governance, and storage metadata into one
 * read-side record. This is NOT a standalone module — it lives within
 * {@code render-module}.</p>
 *
 * @param assetId       unique asset identifier
 * @param assetVersion  version string
 * @param assetType     media type (VIDEO, AUDIO, IMAGE, SUBTITLE, etc.)
 * @param ownerId       owner user or organization
 * @param projectId     parent project
 * @param entityRef     OpenAssetIO entity reference
 * @param xmpUri        XMP sidecar metadata envelope URI
 * @param storageUri    cloud storage reference
 * @param checksum      content hash
 * @param governance    governance metadata block
 * @param createdAt     registration timestamp
 * @param updatedAt     last modification timestamp
 */
public record AssetRegistryRecord(
        String assetId,
        String assetVersion,
        String assetType,
        String ownerId,
        String projectId,
        String entityRef,
        String xmpUri,
        String storageUri,
        String checksum,
        AssetGovernanceMetadata governance,
        Instant createdAt,
        Instant updatedAt) {
}
