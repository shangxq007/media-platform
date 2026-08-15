package com.example.platform.colorimage;

import java.util.Objects;

/**
 * ROADMAP_18 (CI25): typed mastering display metadata — exact Rational
 * semantics only. Invariants: min >= 0, max > 0, max >= min.
 */
public record MasteringDisplayMetadata(
        Chromaticity redPrimary,
        Chromaticity greenPrimary,
        Chromaticity bluePrimary,
        Chromaticity whitePoint,
        Rational minMasteringLuminance,
        Rational maxMasteringLuminance) {

    /** Luminance unit: cd/m². */
    public static final String LUMINANCE_UNIT = "cd/m2";

    public MasteringDisplayMetadata {
        Objects.requireNonNull(redPrimary, "redPrimary");
        Objects.requireNonNull(greenPrimary, "greenPrimary");
        Objects.requireNonNull(bluePrimary, "bluePrimary");
        Objects.requireNonNull(whitePoint, "whitePoint");
        Objects.requireNonNull(minMasteringLuminance, "minMasteringLuminance");
        Objects.requireNonNull(maxMasteringLuminance, "maxMasteringLuminance");
        if (!minMasteringLuminance.isNonNegative()) {
            throw new IllegalArgumentException("min mastering luminance must be >= 0");
        }
        if (!maxMasteringLuminance.isPositive()) {
            throw new IllegalArgumentException("max mastering luminance must be > 0");
        }
        if (maxMasteringLuminance.compareTo(minMasteringLuminance) < 0) {
            throw new IllegalArgumentException("max mastering luminance must be >= min");
        }
    }
}
