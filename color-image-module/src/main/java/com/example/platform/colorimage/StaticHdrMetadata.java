package com.example.platform.colorimage;

import java.util.Objects;
import java.util.Optional;

/**
 * ROADMAP_18 (CI25/CIC2): typed static HDR metadata. At least one semantic
 * component REQUIRED (both absent = INVALID_STATIC_HDR_METADATA). Never an
 * authoritative HDR boolean; absence of static HDR metadata does NOT mean SDR.
 */
public record StaticHdrMetadata(
        Optional<MasteringDisplayMetadata> masteringDisplay,
        Optional<ContentLightMetadata> contentLight) {

    public StaticHdrMetadata {
        Objects.requireNonNull(masteringDisplay, "masteringDisplay");
        Objects.requireNonNull(contentLight, "contentLight");
        if (masteringDisplay.isEmpty() && contentLight.isEmpty()) {
            throw new IllegalArgumentException("StaticHdrMetadata must contain at least one semantic component (CIC2)");
        }
    }

    public static StaticHdrMetadata of(MasteringDisplayMetadata mastering) {
        return new StaticHdrMetadata(Optional.of(mastering), Optional.empty());
    }

    public static StaticHdrMetadata of(ContentLightMetadata contentLight) {
        return new StaticHdrMetadata(Optional.empty(), Optional.of(contentLight));
    }

    public static StaticHdrMetadata of(MasteringDisplayMetadata mastering, ContentLightMetadata contentLight) {
        return new StaticHdrMetadata(Optional.of(mastering), Optional.of(contentLight));
    }
}
