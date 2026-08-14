package com.example.platform.media.domain.time;

import java.io.Serializable;

/**
 * Exact rational source timebase — seconds per tick
 * (SOURCE_MEDIA_EXACT_TIME_AUTHORITY_V1 / F3 contract).
 *
 * <p>TimeBase is a distinct concept from FrameRate: timebase is the exact
 * tick granularity of the source container/codec; frame rate is frames per
 * second. Both are exact rationals. Floating-point seconds are NEVER the
 * semantic authority.
 */
public record TimeBase(long numerator, long denominator) implements Serializable {

    public TimeBase {
        if (denominator <= 0) {
            throw new IllegalArgumentException("TimeBase denominator must be > 0");
        }
        if (numerator <= 0) {
            throw new IllegalArgumentException("TimeBase numerator must be > 0");
        }
        long g = gcd(Math.abs(numerator), denominator);
        numerator = numerator / g;
        denominator = denominator / g;
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    /** Creates a TimeBase from a fractional seconds-per-tick value. */
    public static TimeBase of(long num, long den) {
        return new TimeBase(num, den);
    }
}
