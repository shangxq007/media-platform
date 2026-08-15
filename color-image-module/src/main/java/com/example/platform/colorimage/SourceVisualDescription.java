package com.example.platform.colorimage;

import java.util.Objects;
import java.util.Optional;

/**
 * ROADMAP_18 (CIC1/CIC4/§33): canonical source visual description — exactly ONE
 * ColorDescription authority (sealed variant root; profile variant is NOT a
 * sibling). Alpha presence/interpretation consistency enforced at construction.
 * StaticHdrMetadata optional but non-empty when present.
 */
public record SourceVisualDescription(
        EncodedRasterExtent rasterExtent,
        PixelAspectRatio pixelAspectRatio,
        RasterSampleDescription rasterSampleDescription,
        ColorDescription colorDescription,
        AlphaDescription alphaDescription,
        SourceOrientation sourceOrientation,
        ScanDescription scanDescription,
        Optional<StaticHdrMetadata> staticHdrMetadata) {

    public SourceVisualDescription {
        Objects.requireNonNull(rasterExtent, "rasterExtent");
        Objects.requireNonNull(pixelAspectRatio, "pixelAspectRatio");
        Objects.requireNonNull(rasterSampleDescription, "rasterSampleDescription");
        Objects.requireNonNull(colorDescription, "colorDescription");
        Objects.requireNonNull(alphaDescription, "alphaDescription");
        Objects.requireNonNull(sourceOrientation, "sourceOrientation");
        Objects.requireNonNull(scanDescription, "scanDescription");
        Objects.requireNonNull(staticHdrMetadata, "staticHdrMetadata");
        // CIC4: alpha sample presence <-> interpretation consistency
        if (!rasterSampleDescription.alphaComponentPresent() && alphaDescription != AlphaDescription.NO_ALPHA) {
            throw new IllegalArgumentException(
                    "INCONSISTENT_ALPHA_DESCRIPTION: no alpha component requires AlphaDescription.NO_ALPHA");
        }
        if (rasterSampleDescription.alphaComponentPresent() && alphaDescription == AlphaDescription.NO_ALPHA) {
            throw new IllegalArgumentException(
                    "INCONSISTENT_ALPHA_DESCRIPTION: alpha component present cannot pair NO_ALPHA");
        }
        // CIC2: absent static HDR metadata is the single canonical representation
        if (staticHdrMetadata.isPresent() && staticHdrMetadata.get().masteringDisplay().isEmpty()
                && staticHdrMetadata.get().contentLight().isEmpty()) {
            throw new IllegalArgumentException("INVALID_STATIC_HDR_METADATA: empty instance");
        }
    }
}
