package com.example.platform.shared.time;

import java.io.Serializable;
import java.util.Objects;

/**
 * Canonical time representation using rational numbers (ticks / timeScale).
 * Eliminates floating-point as the Timeline time authority.
 * <p>
 * Invariants:
 * - timeScale > 0
 * - ticks >= 0 for non-negative time
 * - canonical form: gcd(|ticks|, timeScale) == 1 (or ticks == 0)
 */
public final class MediaTime implements Comparable<MediaTime>, Serializable {

    private static final long serialVersionUID = 1L;

    public static final MediaTime ZERO = new MediaTime(0, 1);

    private final long ticks;
    private final long timeScale;

    private MediaTime(long ticks, long timeScale) {
        this.ticks = ticks;
        this.timeScale = timeScale;
    }

    /**
     * Creates a canonical MediaTime from ticks and timeScale.
     */
    public static MediaTime ofTicks(long ticks, long timeScale) {
        if (timeScale <= 0) {
            throw new IllegalArgumentException("timeScale must be > 0: " + timeScale);
        }
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be >= 0: " + ticks);
        }
        if (ticks == 0) {
            return ZERO;
        }
        long g = gcd(ticks, timeScale);
        return new MediaTime(ticks / g, timeScale / g);
    }

    /**
     * Creates MediaTime from a rational value (numerator / denominator).
     */
    public static MediaTime ofRational(long numerator, long denominator) {
        if (denominator <= 0) {
            throw new IllegalArgumentException("denominator must be > 0");
        }
        if (numerator < 0) {
            throw new IllegalArgumentException("numerator must be >= 0");
        }
        return ofTicks(numerator, denominator);
    }

    /**
     * Creates MediaTime representing the given number of microseconds.
     */
    public static MediaTime ofMicros(long micros) {
        return ofTicks(micros, 1_000_000);
    }

    /**
     * Exact MediaTime from integer milliseconds.
     *
     * <p>PROJECTION boundary helper: milliseconds are not canonical merge
     * authority; this exists for legacy document/Duration interop and test
     * fixtures (1 ms = 1/1000 s, exact rational).</p>
     */
    public static MediaTime ofMillis(long millis) {
        return ofTicks(millis, 1_000);
    }

    /**
     * Creates MediaTime representing the given number of nanoseconds.
     */
    public static MediaTime ofNanos(long nanos) {
        return ofTicks(nanos, 1_000_000_000);
    }

    /**
     * Creates MediaTime from frames at a given frame rate.
     */
    public static MediaTime ofFrames(long frames, long numerator, long denominator) {
        if (numerator <= 0 || denominator <= 0) {
            throw new IllegalArgumentException("Frame rate must be positive");
        }
        // frames / (numerator / denominator) = frames * denominator / numerator
        long num = Math.multiplyExact(frames, denominator);
        return ofTicks(num, numerator);
    }

    /**
     * Returns a new MediaTime with the given schema version for serialization.
     */
    public MediaTimeSchema toSchema(String schemaVersion) {
        return new MediaTimeSchema(ticks, timeScale, schemaVersion);
    }

    public long ticks() { return ticks; }
    public long timeScale() { return timeScale; }

    /**
     * Adds two MediaTimes. Result is canonical.
     */
    public MediaTime add(MediaTime other) {
        // a/b + c/d = (a*d + c*b) / (b*d)
        long newTicks = Math.addExact(
            Math.multiplyExact(this.ticks, other.timeScale),
            Math.multiplyExact(other.ticks, this.timeScale)
        );
        long newScale = Math.multiplyExact(this.timeScale, other.timeScale);
        return ofTicks(newTicks, newScale);
    }

    /**
     * Subtracts another MediaTime. Requires this >= other.
     */
    public MediaTime subtract(MediaTime other) {
        // a/b - c/d = (a*d - c*b) / (b*d)
        long newTicks = Math.subtractExact(
            Math.multiplyExact(this.ticks, other.timeScale),
            Math.multiplyExact(other.ticks, this.timeScale)
        );
        long newScale = Math.multiplyExact(this.timeScale, other.timeScale);
        return ofTicks(newTicks, newScale);
    }

    /**
     * Multiplies by a rational rate (e.g., playback rate).
     * rate is expressed as rateNum / rateDen.
     */
    public MediaTime multiplyRational(long rateNum, long rateDen) {
        if (rateNum < 0) {
            throw new IllegalArgumentException("Rate numerator must be >= 0");
        }
        if (rateDen <= 0) {
            throw new IllegalArgumentException("Rate denominator must be > 0");
        }
        long newTicks = Math.multiplyExact(this.ticks, rateNum);
        long newScale = Math.multiplyExact(this.timeScale, rateDen);
        return ofTicks(newTicks, newScale);
    }

    /**
     * Divides by a positive scalar.
     */
    public MediaTime divideByScalar(long divisor) {
        if (divisor <= 0) {
            throw new IllegalArgumentException("Divisor must be > 0");
        }
        return ofTicks(this.ticks, Math.multiplyExact(this.timeScale, divisor));
    }

    /**
     * Multiplies by a positive scalar.
     */
    public MediaTime multiplyByScalar(long scalar) {
        if (scalar < 0) {
            throw new IllegalArgumentException("Scalar must be >= 0");
        }
        return ofTicks(Math.multiplyExact(this.ticks, scalar), this.timeScale);
    }

    /**
     * Returns true if this >= other.
     */
    public boolean isGreaterThanOrEqualTo(MediaTime other) {
        // a/b >= c/d iff a*d >= c*b
        return Math.multiplyExact(this.ticks, other.timeScale)
            >= Math.multiplyExact(other.ticks, this.timeScale);
    }

    /**
     * Returns true if this > other.
     */
    public boolean isGreaterThan(MediaTime other) {
        return Math.multiplyExact(this.ticks, other.timeScale)
            > Math.multiplyExact(other.ticks, this.timeScale);
    }

    /**
     * Returns true if this < other.
     */
    public boolean isLessThan(MediaTime other) {
        return Math.multiplyExact(this.ticks, other.timeScale)
            < Math.multiplyExact(other.ticks, this.timeScale);
    }

    /**
     * Returns true if this <= other.
     */
    public boolean isLessThanOrEqualTo(MediaTime other) {
        return Math.multiplyExact(this.ticks, other.timeScale)
            <= Math.multiplyExact(other.ticks, this.timeScale);
    }

    /**
     * Returns true if this == other.
     */
    public boolean isEqualTo(MediaTime other) {
        // Canonical form guarantees same representation for equal values
        return this.ticks == other.ticks && this.timeScale == other.timeScale;
    }

    /**
     * Returns the minimum of this and other.
     */
    public MediaTime min(MediaTime other) {
        return isGreaterThanOrEqualTo(other) ? other : this;
    }

    /**
     * Returns the maximum of this and other.
     */
    public MediaTime max(MediaTime other) {
        return isGreaterThanOrEqualTo(other) ? this : other;
    }

    /**
     * Returns the ratio this / other as a double (for non-authoritative reporting only).
     */
    public double ratioOver(MediaTime other) {
        return (double) this.ticks * other.timeScale / (double) (this.timeScale * other.ticks);
    }

    @Override
    public int compareTo(MediaTime other) {
        // a/b vs c/d -> compare a*d vs c*b
        long lhs = Math.multiplyExact(this.ticks, other.timeScale);
        long rhs = Math.multiplyExact(other.ticks, this.timeScale);
        return Long.compare(lhs, rhs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MediaTime that)) return false;
        return ticks == that.ticks && timeScale == that.timeScale;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ticks, timeScale);
    }

    @Override
    /** Canonical string form "ticks/timeScale" (e.g. "1001/800"); zero renders as "0". */
    public String toString() {
        if (ticks == 0) return "0";
        return ticks + "/" + timeScale;
    }

    /**
     * Parses the canonical string form produced by {@link #toString()}
     * ("ticks/timeScale" or "0") back into an exact MediaTime.
     */
    public static MediaTime parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MediaTime value must not be blank");
        }
        String v = value.trim();
        if (v.equals("0")) {
            return ZERO;
        }
        int slash = v.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("Invalid MediaTime canonical form (expected ticks/timeScale): " + value);
        }
        long ticks = Long.parseLong(v.substring(0, slash).trim());
        long timeScale = Long.parseLong(v.substring(slash + 1).trim());
        return ofTicks(ticks, timeScale);
    }

    /**
     * Exact conversion to a frame index at the given exact rational rate.
     *
     * <p>frame = ticks * rate.num / (timeScale * rate.den), computed exactly.
     * For canonical frame-derived times (created via {@link #ofFrames(long, long, long)})
     * this is the exact inverse and returns the original frame. If the time is
     * not an exact frame boundary at the given rate, the division does not
     * divide evenly and the floor is returned — callers on the canonical
     * persistence boundary must use {@link #toFrameExact(FrameRate)} to enforce
     * exactness.
     */
    public long toFrame(FrameRate rate) {
        if (ticks == 0) {
            return 0L;
        }
        java.math.BigInteger num = java.math.BigInteger.valueOf(ticks)
                .multiply(rate.numerator());
        java.math.BigInteger den = java.math.BigInteger.valueOf(timeScale)
                .multiply(java.math.BigInteger.valueOf(rate.denominator()));
        return num.divide(den).longValueExact();
    }

    /**
     * Exact frame conversion with integrality enforcement.
     *
     * <p>Returns the exact frame index when the time is an exact frame
     * boundary at the given rate; throws {@link ArithmeticException} when the
     * value is not representable as an integer frame (canonical persisted
     * merge output must never silently quantize a non-frame-aligned value).
     */
    public long toFrameExact(FrameRate rate) {
        if (ticks == 0) {
            return 0L;
        }
        java.math.BigInteger num = java.math.BigInteger.valueOf(ticks)
                .multiply(rate.numerator());
        java.math.BigInteger den = java.math.BigInteger.valueOf(timeScale)
                .multiply(java.math.BigInteger.valueOf(rate.denominator()));
        java.math.BigInteger[] qr = num.divideAndRemainder(den);
        if (qr[1].signum() != 0) {
            throw new ArithmeticException(
                    "Time " + this + " is not an exact frame boundary at rate " + rate);
        }
        return qr[0].longValueExact();
    }

    private static long gcd(long a, long b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            long t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    /**
     * Schema-serializable form of MediaTime.
     */
    public record MediaTimeSchema(long ticks, long timeScale, String schemaVersion) {}
}
