package com.example.platform.media.domain.identity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Canonical source-media identity (MEDIA_ASSET_IDENTITY_AUTHORITY_V1).
 *
 * <p>A {@link MediaAssetId} is the stable semantic identity of a source media
 * entity. It is independent of:
 * <ul>
 *   <li>storage location (never a storage URI / key)</li>
 *   <li>content digest (never a checksum)</li>
 *   <li>artifact identity (ArtifactId references physical content versions)</li>
 *   <li>external locators (entityRef / logicalUri)</li>
 *   <li>provider, timeline, or worker concepts</li>
 * </ul>
 *
 * <p>Identity is stable across re-probe, metadata enrichment, storage
 * relocation, and (where legal under the frozen lifecycle contract) source
 * replacement / re-link.
 */
public record MediaAssetId(String value) implements Serializable {

    public MediaAssetId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MediaAssetId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }

    /** Canonical serialization form: the raw id string (Jsons-compatible). */
    public String asString() {
        return value;
    }

    public static MediaAssetId of(String value) {
        return new MediaAssetId(Objects.requireNonNull(value));
    }
}
