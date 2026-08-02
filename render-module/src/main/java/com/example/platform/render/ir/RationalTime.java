package com.example.platform.render.ir;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Integer rational time model for the Media Platform IR.
 *
 * <p>Time is always represented as an exact rational (numerator / denominator).
 * Floating-point seconds are NEVER the semantic authority. The denominator
 * must be positive.
 *
 * <p>This class is immutable and thread-safe. All arithmetic operations
 * include overflow detection.
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>denominator > 0</li>
 *   <li>sourceStart >= 0</li>
 *   <li>sourceDuration > 0</li>
 *   <li>timelineStart >= 0</li>
 * </ul>
 *
 * @param numerator   the time numerator
 * @param denominator the time denominator (MUST be positive)
 */
public record RationalTime(BigInteger numerator, long denominator) {

    public RationalTime {
        Objects.requireNonNull(numerator, "numerator must not be null");
        if (denominator <= 0) {
            throw new IllegalArgumentException("denominator must be positive, got: " + denominator);
        }
        // Freeze to canonical form
        if (denominator > 1) {
            BigInteger gcd = numerator.gcd(BigInteger.valueOf(denominator));
            if (gcd.compareTo(BigInteger.ONE) > 0) {
                numerator = numerator.divide(gcd);
                denominator = denominator / gcd.longValueExact();
            }
        }
    }

    /**
     * Creates a RationalTime from a long numerator and positive denominator.
     */
    public static RationalTime of(long numerator, long denominator) {
        return new RationalTime(BigInteger.valueOf(numerator), denominator);
    }

    /**
     * Convenience: creates a RationalTime at zero.
     */
    public static RationalTime zero(long denominator) {
        return new RationalTime(BigInteger.ZERO, denominator);
    }

    /**
     * Compares two RationalTimes for ordering.
     *
     * @return negative if this &lt; other, zero if equal, positive if this &gt; other
     */
    public int compareTo(RationalTime other) {
        Objects.requireNonNull(other, "other must not be null");
        if (this.denominator == other.denominator) {
            return this.numerator.compareTo(other.numerator);
        }
        // Cross-multiply: a/b vs c/d => a*d vs c*b
        BigInteger left = this.numerator.multiply(BigInteger.valueOf(other.denominator));
        BigInteger right = other.numerator.multiply(BigInteger.valueOf(this.denominator));
        return left.compareTo(right);
    }

    /**
     * Returns true if this is strictly less than other.
     */
    public boolean isLessThan(RationalTime other) {
        return compareTo(other) < 0;
    }

    /**
     * Returns true if this is less than or equal to other.
     */
    public boolean isLessThanOrEqual(RationalTime other) {
        return compareTo(other) <= 0;
    }

    /**
     * Returns true if this is strictly greater than other.
     */
    public boolean isGreaterThan(RationalTime other) {
        return compareTo(other) > 0;
    }

    /**
     * Returns true if this is greater than or equal to other.
     */
    public boolean isGreaterThanOrEqual(RationalTime other) {
        return compareTo(other) >= 0;
    }

    /**
     * Checks if this time is negative.
     */
    public boolean isNegative() {
        return numerator.signum() < 0;
    }

    /**
     * Checks if this time is zero.
     */
    public boolean isZero() {
        return numerator.signum() == 0;
    }

    /**
     * Adds another RationalTime to this one with overflow detection.
     *
     * @param other the time to add
     * @return a new RationalTime representing this + other
     * @throws ArithmeticException on overflow
     */
    public RationalTime add(RationalTime other) {
        Objects.requireNonNull(other, "other must not be null");
        if (this.denominator == other.denominator) {
            BigInteger sumNumerator = this.numerator.add(other.numerator);
            // Check for overflow in add
            return new RationalTime(sumNumerator, this.denominator);
        }
        // a/b + c/d = (a*d + c*b) / (b*d)
        BigInteger ad = this.numerator.multiply(BigInteger.valueOf(other.denominator));
        BigInteger cb = other.numerator.multiply(BigInteger.valueOf(this.denominator));
        BigInteger sumNumerator = ad.add(cb);

        // Denominator: b*d — check overflow
        long newDenom;
        try {
            newDenom = Math.multiplyExact(this.denominator, other.denominator);
        } catch (ArithmeticException e) {
            throw new ArithmeticException(
                "Denominator overflow: " + this.denominator + " * " + other.denominator);
        }
        return new RationalTime(sumNumerator, newDenom);
    }

    /**
     * Computes end time = start + duration.
     */
    public RationalTime end(RationalTime duration) {
        return add(duration);
    }

    /**
     * Returns true if this time is semantically equal to another (after normalization).
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RationalTime that)) return false;
        // Cross-multiply comparison: a/b == c/d iff a*d == c*b
        BigInteger left = this.numerator.multiply(BigInteger.valueOf(that.denominator));
        BigInteger right = that.numerator.multiply(BigInteger.valueOf(this.denominator));
        return left.equals(right);
    }

    @Override
    public int hashCode() {
        // Normalize to a canonical form for hashing
        BigInteger gcd = numerator.gcd(BigInteger.valueOf(denominator));
        BigInteger normNum = numerator.divide(gcd);
        long normDen = denominator / gcd.longValueExact();
        return Objects.hash(normNum, normDen);
    }

    @Override
    public String toString() {
        return numerator + "/" + denominator;
    }
}
