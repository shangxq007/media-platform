package com.example.platform.media.domain.media;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.locator.ExternalLocator;
import java.io.Serializable;
import java.time.Instant;

/**
 * Canonical source media entity (MEDIA_CANONICAL_MODEL_V2).
 *
 * <p>MediaAsset owns ONLY source-media semantics:
 * <ul>
 *   <li>stable semantic identity (MediaAssetId)</li>
 *   <li>tenant/project scope</li>
 *   <li>media version</li>
 *   <li>external locator (never identity)</li>
 *   <li>governance/publish metadata</li>
 * </ul>
 *
 * <p>MediaAsset does NOT own: storage binding (STORAGE_REF projection),
 * content digest as identity, physical lifecycle (artifact authority),
 * stream/structural truth (MediaStream / source description model).
 */
public record MediaAsset(
        MediaAssetId id,
        String tenantId,
        String projectId,
        String mediaVersion,
        ExternalLocator externalLocator,
        String classification,
        String license,
        String retentionPolicy,
        String securityLevel,
        boolean containsPii,
        boolean aiGenerated,
        String publishStatus,
        Instant createdAt,
        Instant updatedAt) implements Serializable {

    public MediaAsset {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId must not be blank");
        }
    }
}
