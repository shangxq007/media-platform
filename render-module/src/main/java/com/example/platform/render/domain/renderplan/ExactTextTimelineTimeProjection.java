package com.example.platform.render.domain.renderplan;

import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.shared.time.MediaTime;
import java.math.BigInteger;

/**
 * TIMED_TEXT temporal bridge (Phase A, FROZEN option T2) — checked exact
 * #20-owned projection of authored FontRational timeline timing into the
 * RenderExecutionCoverage coordinate domain (MediaTime exact rational
 * seconds).
 *
 * <p>EXACTNESS_RULE: value-preserving rational mapping (numerator→ticks,
 * denominator→timeScale). OVERFLOW_RULE: BigInteger values not exactly
 * representable in MediaTime's bounded long ticks/timeScale FAIL CLOSED
 * (typed deterministic result — never rounded, never clamped).
 *
 * <p>OWNER=#20 renderplan (consumed inside DefaultRenderMaterializer). #21
 * never sees FontRational. TextElement remains the authored authority.
 */
public final class ExactTextTimelineTimeProjection {

    private ExactTextTimelineTimeProjection() {
    }

    /** Result: exact projection or typed fail-closed overflow. */
    public sealed interface Result permits Projected, Overflow {
        /** Whether the projection succeeded exactly. */
        boolean exact();
    }

    /** Exact MediaTime projection. */
    public record Projected(MediaTime mediaTime) implements Result {
        @Override
        public boolean exact() {
            return true;
        }
    }

    /** Typed fail-closed overflow (no rounding, no clamping). */
    public record Overflow(String reason) implements Result {
        @Override
        public boolean exact() {
            return false;
        }
    }

    /**
     * Exact projection of a single FontRational into MediaTime.
     * Fails closed when the normalized rational does not fit long ticks/timeScale.
     */
    public static Result project(FontRational value) {
        if (value == null) {
            return new Overflow("null FontRational");
        }
        BigInteger num = value.numerator();
        BigInteger den = value.denominator();
        BigInteger gcd = num.gcd(den);
        if (gcd.signum() > 0) {
            num = num.divide(gcd);
            den = den.divide(gcd);
        }
        if (den.signum() < 0) {
            num = num.negate();
            den = den.negate();
        }
        try {
            long ticks = num.longValueExact();
            long scale = den.longValueExact();
            return new Projected(MediaTime.ofTicks(ticks, scale));
        } catch (ArithmeticException e) {
            return new Overflow("FontRational " + num + "/" + den
                    + " not exactly representable in MediaTime (long ticks/timeScale)");
        }
    }

    /**
     * Exact rational sum start + duration, then projected.
     * coverageEnd = exact(start + duration).
     */
    public static Result projectEnd(FontRational start, FontRational duration) {
        if (start == null || duration == null) {
            return new Overflow("null start/duration");
        }
        BigInteger num = start.numerator().multiply(duration.denominator())
                .add(duration.numerator().multiply(start.denominator()));
        BigInteger den = start.denominator().multiply(duration.denominator());
        if (den.signum() == 0) {
            return new Overflow("zero denominator");
        }
        BigInteger gcd = num.gcd(den);
        if (gcd.signum() > 0) {
            num = num.divide(gcd);
            den = den.divide(gcd);
        }
        if (den.signum() < 0) {
            num = num.negate();
            den = den.negate();
        }
        try {
            long ticks = num.longValueExact();
            long scale = den.longValueExact();
            return new Projected(MediaTime.ofTicks(ticks, scale));
        } catch (ArithmeticException e) {
            return new Overflow("start+duration " + num + "/" + den
                    + " not exactly representable in MediaTime (long ticks/timeScale)");
        }
    }
}
