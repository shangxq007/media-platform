package com.example.platform.colorimage;

import java.util.Objects;

/**
 * ROADMAP_18 (CI10/CI14): exact chromaticity (x, y) in the CIE xyY sense —
 * canonical exact Rational semantics only; zero float/double equality.
 * Invariants: x >= 0, y >= 0, and both within the bounded physical domain
 * [0, 1] for V1 (invalid -> fail closed).
 */
public record Chromaticity(Rational x, Rational y) {

    public Chromaticity {
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");
        if (!x.isNonNegative() || !y.isNonNegative()) {
            throw new IllegalArgumentException("chromaticity coordinates must be non-negative");
        }
        if (x.compareTo(Rational.of(1, 1)) > 0 || y.compareTo(Rational.of(1, 1)) > 0) {
            throw new IllegalArgumentException("chromaticity coordinates must be <= 1 for V1");
        }
    }

    public static Chromaticity of(long xNum, long xDen, long yNum, long yDen) {
        return new Chromaticity(Rational.of(xNum, xDen), Rational.of(yNum, yDen));
    }

    /** Exact parse from decimal strings, e.g. Chromaticity.of("0.3127", "0.3290"). */
    public static Chromaticity of(String x, String y) {
        return new Chromaticity(Rational.of(x), Rational.of(y));
    }
}
