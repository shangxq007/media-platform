package com.example.platform.render.domain.asset;

import java.time.Instant;

/**
 * Domain model for a project asset (video, image, audio, subtitle, etc.).
 *
 * <p>Assets are stored in object storage and referenced by timeline clips.
 * The {@code storageKey} is a validated, tenant-scoped storage path — not a signed URL.</p>
 */
public record Asset(
        String id,
        String tenantId,
        String projectId,
        String storageKey,
        String mediaType,
        String filename,
        Long sizeBytes,
        String checksum,
        Long durationMs,
        Integer width,
        Integer height,
        String assetVersion,
        String ownerId,
        String entityRef,
        String classification,
        String license,
        String retentionPolicy,
        String securityLevel,
        boolean containsPii,
        boolean aiGenerated,
        String publishStatus,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Media type classification.
     */
    public enum MediaType {
        VIDEO, IMAGE, AUDIO, SUBTITLE, UNKNOWN
    }

    /**
     * Returns true if this asset is a video type.
     */
    public boolean isVideo() {
        return "VIDEO".equals(mediaType);
    }

    /**
     * Returns true if this asset is an image type.
     */
    public boolean isImage() {
        return "IMAGE".equals(mediaType);
    }

    /**
     * Returns true if this asset is an audio type.
     */
    public boolean isAudio() {
        return "AUDIO".equals(mediaType);
    }
}
