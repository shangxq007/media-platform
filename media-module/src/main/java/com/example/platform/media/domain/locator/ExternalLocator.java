package com.example.platform.media.domain.locator;

import java.io.Serializable;

/**
 * EXTERNAL_LOCATOR — an external/source locator abstraction
 * (MEDIA_REFERENCE_TAXONOMY_V1 kind 5).
 *
 * <p>EXTERNAL_LOCATOR_IS_NOT_CANONICAL_MEDIA_IDENTITY_V1: an external locator
 * (OpenAssetIO-style entity reference, OTIO external reference, logical URI)
 * identifies a location in an external system. It is never canonical media
 * identity and never substitutes for {@code MediaAssetId}.
 */
public record ExternalLocator(String kind, String value) implements Serializable {

    public ExternalLocator {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("locator kind must not be blank");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("locator value must not be blank");
        }
    }

    public static ExternalLocator of(String kind, String value) {
        return new ExternalLocator(kind, value);
    }
}
