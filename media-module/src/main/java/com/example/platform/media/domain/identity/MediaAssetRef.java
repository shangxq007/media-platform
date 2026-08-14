package com.example.platform.media.domain.identity;

import java.io.Serializable;

/**
 * MEDIA_SOURCE_REF — canonical typed reference to a source media entity
 * (MEDIA_REFERENCE_TAXONOMY_V1 kind 2).
 *
 * <p>Consumers (Timeline clips, render inputs, workflow payloads, adapters)
 * reference source media through this type instead of bare id strings.
 * The reference carries only identity; structural truth lives on the
 * MediaAsset / MediaStream canonical model.
 */
public record MediaAssetRef(MediaAssetId mediaAssetId) implements Serializable {

    public MediaAssetRef {
        if (mediaAssetId == null) {
            throw new IllegalArgumentException("mediaAssetId must not be null");
        }
    }

    public static MediaAssetRef of(String value) {
        return new MediaAssetRef(MediaAssetId.of(value));
    }
}
