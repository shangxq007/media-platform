package com.example.platform.colorimage;

import java.util.Objects;

/**
 * ROADMAP_18 (CI18): exact positive pixel aspect ratio (Rational). Display
 * aspect ratio is DERIVED from extent + PAR — never a second authority.
 */
public record PixelAspectRatio(Rational value) {

    public PixelAspectRatio {
        Objects.requireNonNull(value, "value");
        if (!value.isPositive()) {
            throw new IllegalArgumentException("pixel aspect ratio must be positive");
        }
    }

    public static PixelAspectRatio of(long num, long den) {
        return new PixelAspectRatio(Rational.of(num, den));
    }

    public static PixelAspectRatio square() {
        return new PixelAspectRatio(Rational.of(1, 1));
    }
}
