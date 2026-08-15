package com.example.platform.colorimage;

import java.util.Objects;

/**
 * ROADMAP_18 (CI16/CIC4): typed decoded sample organization — semantic
 * components, never FFmpeg pix_fmt identity. Explicit alpha component presence
 * (NOT interpretation).
 */
public record RasterSampleDescription(
        SampleFamily family,
        SampleOrganization organization,
        int bitDepth,
        ChromaSubsampling chromaSubsampling,
        ChromaLocation chromaLocation,
        boolean alphaComponentPresent) {

    public static final int MAX_BIT_DEPTH = 64;

    public RasterSampleDescription {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(organization, "organization");
        Objects.requireNonNull(chromaSubsampling, "chromaSubsampling");
        Objects.requireNonNull(chromaLocation, "chromaLocation");
        if (bitDepth <= 0 || bitDepth > MAX_BIT_DEPTH) {
            throw new IllegalArgumentException("bit depth must be within (0, " + MAX_BIT_DEPTH + "]");
        }
        if (family == SampleFamily.RGB && chromaSubsampling != ChromaSubsampling.NONE) {
            throw new IllegalArgumentException("RGB-family samples do not carry chroma subsampling");
        }
    }

    public static RasterSampleDescription rgb(int bitDepth, boolean alpha) {
        return new RasterSampleDescription(SampleFamily.RGB, SampleOrganization.INTERLEAVED,
                bitDepth, ChromaSubsampling.NONE, ChromaLocation.UNSPECIFIED, alpha);
    }

    public static RasterSampleDescription ycbcr(int bitDepth, ChromaSubsampling subsampling) {
        return new RasterSampleDescription(SampleFamily.YCbCr, SampleOrganization.PLANAR,
                bitDepth, subsampling, ChromaLocation.LEFT, false);
    }
}

/** Planar vs interleaved sample organization. */
