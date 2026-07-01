package com.example.platform.render.app.product;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for preview render artifact metadata.
 *
 * <p>Carries safe Product metadata and storage size/checksum info
 * for API responses. Does NOT expose:
 * <ul>
 *   <li>Internal filesystem paths (rootPath, relativePath, absolutePath)</li>
 *   <li>Storage provider internals (bucket, key, signed URLs)</li>
 *   <li>Provider/backend/environment selection details</li>
 * </ul>
 *
 * <p>Constructed by {@link PreviewArtifactQueryService} from Product
 * and StorageReference domain objects.</p>
 */
public record PreviewArtifactResponse(
        /** Product identifier. */
        String productId,
        /** Project identifier. */
        String projectId,
        /** Product type (e.g., PREVIEW, FINAL_RENDER, THUMBNAIL, PROXY). */
        String productType,
        /** Product lifecycle status. */
        String status,
        /** MIME type of the artifact (e.g., video/mp4, image/png). */
        String mimeType,
        /** How the artifact is physically represented. */
        String representationKind,
        /** File size in bytes (0 if no storage reference). */
        long fileSize,
        /** SHA-256 checksum (null if no storage reference). */
        String checksum,
        /** Content hash for deduplication (null if no storage reference). */
        String contentHash,
        /** Producer type (e.g., ffmpeg, remotion). */
        String producerType,
        /** Producer identifier. */
        String producerId,
        /** Source timeline revision ID (for timeline-derived products). */
        String sourceTimelineRevisionId,
        /** Owner asset ID (for root products). */
        String ownerAssetId,
        /** Number of upstream dependencies. */
        int upstreamDependencyCount,
        /** Number of downstream dependents. */
        int downstreamDependentCount,
        /** Product version. */
        int version,
        /** Product creation timestamp. */
        Instant createdAt,
        /** Product last update timestamp. */
        Instant updatedAt,
        /** Upstream dependency product IDs (optional, populated on request). */
        List<String> upstreamDependencyIds,
        /** Downstream dependent product IDs (optional, populated on request). */
        List<String> downstreamDependentIds
) {

    /**
     * Returns true if this product has a storage reference with file metadata.
     */
    public boolean hasStorageReference() {
        return checksum != null && fileSize > 0;
    }

    /**
     * Returns true if this product is in READY status.
     */
    public boolean isReady() {
        return "READY".equals(status);
    }

    /**
     * Returns true if this product is in FAILED status.
     */
    public boolean isFailed() {
        return "FAILED".equals(status);
    }

    /**
     * Returns true if this product has upstream dependencies.
     */
    public boolean hasDependencies() {
        return upstreamDependencyCount > 0;
    }
}
