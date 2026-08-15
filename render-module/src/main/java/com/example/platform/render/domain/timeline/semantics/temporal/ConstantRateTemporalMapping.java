package com.example.platform.render.domain.timeline.semantics.temporal;

import com.example.platform.render.domain.timeline.semantics.clip.MediaClip;
import java.util.Objects;

/**
 * TEMPORAL_MAPPING_FOUNDATION_V1 (TM9/R1/R2): constant-rate forward/reverse
 * traversal.
 *
 * <p>Canonical shape: positive exact rational rate magnitude + explicit
 * {@link PlaybackDirection}. Identity IS this type with rate 1/1 + FORWARD
 * (R1) — there is no separate IdentityTemporalMapping subtype and no
 * "IDENTITY" discriminator.
 *
 * <p>Rate invariants (TM3/R2): normalized gcd, denominator != 0, strictly > 0.
 * No floating point. Direction does not change duration magnitude (R3).
 */
public record ConstantRateTemporalMapping(MediaClip.Rational rate, PlaybackDirection direction)
        implements TemporalMapping {

    public ConstantRateTemporalMapping {
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(direction, "direction");
        // R2: rate must be strictly positive; MediaClip.Rational already rejects
        // numerator <= 0 / denominator <= 0. Normalization happens in of().
        if (rate.numerator() <= 0 || rate.denominator() <= 0) {
            throw new IllegalArgumentException("rate must be strictly positive rational");
        }
    }

    /**
     * Factory with gcd normalization: 2/2 -> 1/1, 4/2 -> 2/1, 2/4 -> 1/2.
     * Identity canonical form = {@code ConstantRateTemporalMapping(1/1, FORWARD)}.
     */
    public static ConstantRateTemporalMapping of(long numerator, long denominator, PlaybackDirection direction) {
        if (numerator <= 0 || denominator <= 0) {
            throw new IllegalArgumentException("rate must be strictly positive rational");
        }
        long g = gcd(numerator, denominator);
        return new ConstantRateTemporalMapping(
                new MediaClip.Rational(numerator / g, denominator / g), direction);
    }

    public static ConstantRateTemporalMapping of(MediaClip.Rational rate, PlaybackDirection direction) {
        return of(rate.numerator(), rate.denominator(), direction);
    }

    /** Identity canonical form: 1/1 + FORWARD (R1). */
    public static ConstantRateTemporalMapping identity() {
        return new ConstantRateTemporalMapping(new MediaClip.Rational(1, 1), PlaybackDirection.FORWARD);
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    @Override
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Kind kind() {
        return Kind.CONSTANT_RATE;
    }
}
