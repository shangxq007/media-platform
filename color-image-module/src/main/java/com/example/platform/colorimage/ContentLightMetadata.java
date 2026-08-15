package com.example.platform.colorimage;

import java.util.Objects;

/**
 * ROADMAP_18 (CI25): typed content-light metadata — MaxCLL / MaxFALL,
 * non-negative exact values. Units: cd/m². Never derived when absent; no
 * speculative MaxCLL >= MaxFALL invariant.
 */
public record ContentLightMetadata(Rational maxCll, Rational maxFall) {

    public static final String LUMINANCE_UNIT = "cd/m2";

    public ContentLightMetadata {
        Objects.requireNonNull(maxCll, "maxCll");
        Objects.requireNonNull(maxFall, "maxFall");
        if (!maxCll.isNonNegative()) {
            throw new IllegalArgumentException("MaxCLL must be >= 0");
        }
        if (!maxFall.isNonNegative()) {
            throw new IllegalArgumentException("MaxFALL must be >= 0");
        }
    }
}
