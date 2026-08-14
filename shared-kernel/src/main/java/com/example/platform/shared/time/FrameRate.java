package com.example.platform.shared.time;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Exact rational frame-rate domain value (C1-CNM1 frozen contract).
 *
 * <p>FrameRate is a DISTINCT domain type: it is not MediaTime, not
 * RationalTime, not AspectRatio, not PlaybackRate. Exact-rational mechanics
 * (BigInteger numerator, positive denominator, gcd normalization, exact
 * equality/comparison) follow the repository {@code ir/RationalTime}
 * precedent, but the type remains explicitly a frame rate.</p>
 *
 * <p>Framework-neutral: no Spring/Jackson annotations on the domain value.
 * As a record, Jackson 2.x deserializes it via the canonical constructor
 * ({@code num}/{@code den} JSON properties), preserving the canonical
 * {@code rate{num,den}} wire contract.</p>
 *
 * @param numerator   exact frame-rate numerator (must be &gt; 0)
 * @param denominator exact frame-rate denominator (must be &gt; 0)
 */
public record FrameRate(BigInteger numerator, long denominator) {

    /**
     * Canonical constructor: positive numerator, positive denominator,
     * gcd-normalized (60000/2002 == 30000/1001 serialize identically).
     */
    public FrameRate {
        Objects.requireNonNull(numerator, "numerator must not be null");
        if (numerator.signum() <= 0) {
            throw new IllegalArgumentException("numerator must be positive, got: " + numerator);
        }
        if (denominator <= 0) {
            throw new IllegalArgumentException("denominator must be positive, got: " + denominator);
        }
        if (denominator > 1) {
            BigInteger gcd = numerator.gcd(BigInteger.valueOf(denominator));
            if (gcd.compareTo(BigInteger.ONE) > 0) {
                numerator = numerator.divide(gcd);
                denominator = denominator / gcd.longValueExact();
            }
        }
    }

    /** Exact frame rate from long components. */
    public static FrameRate of(long numerator, long denominator) {
        return new FrameRate(BigInteger.valueOf(numerator), denominator);
    }

    /** Exact frame rate from BigInteger numerator and long denominator. */
    public static FrameRate of(BigInteger numerator, long denominator) {
        return new FrameRate(numerator, denominator);
    }

    /** Exact integer frame rate (e.g. 30 fps = 30/1). */
    public static FrameRate ofFps(long fps) {
        return of(fps, 1);
    }

    /**
     * Integer fps projection (EXACT).
     *
     * <p>Returns the exact integer fps ONLY when the rate is an integer
     * (denominator == 1 after normalization); throws
     * {@link ArithmeticException} for fractional rates (e.g. 30000/1001 is
     * NOT 29 and NOT 30 — it is not an integer). Fractional rates must never
     * silently become integer fps: providers that only accept integer fps
     * must reject fractional rates explicitly at their boundary.</p>
     *
     * <p>PROJECTION ONLY — used at provider boundaries that require an
     * integer fps (e.g. render job mapping). Never a canonical authority;
     * canonical persisted values always retain the exact rational form.</p>
     */
    public int intFps() {
        return toIntegerExact();
    }

    /**
     * Exact integer fps conversion (see {@link #intFps()}).
     *
     * @throws ArithmeticException when the rate is fractional (not an
     *         integer number of frames per second)
     */
    public int toIntegerExact() {
        if (denominator != 1L) {
            throw new ArithmeticException(
                    "Frame rate " + this + " is fractional; integer fps projection is not exact");
        }
        return numerator.intValueExact();
    }

    /**
     * True when the rate is an exact integer fps (denominator == 1).
     */
    public boolean isInteger() {
        return denominator == 1L;
    }

    /**
     * Explicit approximate projection (double fps).
     *
     * <p>Allowed ONLY for renderer calculation, UI/display, provider APIs
     * explicitly defined as approximate floating values, metrics/diagnostics,
     * and derived computation. Must NEVER flow back into persisted revision,
     * diff/merge, identity, or canonical rate/time authority.</p>
     */
    public double toDouble() {
        return numerator.doubleValue() / denominator;
    }

    /**
     * Exact rational comparison (cross-multiplied; no floating intermediate).
     * Integer rates and fractional rates compare exactly:
     * 30000/1001 &gt; 29/1 (true), 24/1 &lt; 25/1 (true).
     */
    public int compareTo(FrameRate other) {
        if (other == null) {
            return 1;
        }
        // num/den vs other.num/other.den  <=>  num*other.den vs other.num*den
        return numerator.multiply(BigInteger.valueOf(other.denominator))
                .compareTo(other.numerator.multiply(BigInteger.valueOf(denominator)));
    }

    /**
     * Canonical string form "num/den" (e.g. "24000/1001"); integer rates
     * render as "num/1".
     */
    @Override
    public String toString() {
        return numerator + "/" + denominator;
    }
}
