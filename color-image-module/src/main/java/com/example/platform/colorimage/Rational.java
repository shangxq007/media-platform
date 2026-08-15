package com.example.platform.colorimage;

import java.math.BigInteger;
import java.util.Objects;

/**
 * ROADMAP_18 (CI14): exact deterministic rational — canonical numeric semantics
 * for chromaticity / PAR / luminance. Never double/float for semantic equality.
 * Normalized: denominator > 0, gcd-reduced; 1/2 equals 2/4.
 */
public final class Rational implements Comparable<Rational> {

    private final BigInteger numerator;
    private final BigInteger denominator;

    public Rational(BigInteger numerator, BigInteger denominator) {
        Objects.requireNonNull(numerator, "numerator");
        Objects.requireNonNull(denominator, "denominator");
        if (denominator.signum() == 0) {
            throw new IllegalArgumentException("denominator must be non-zero");
        }
        if (denominator.signum() < 0) {
            numerator = numerator.negate();
            denominator = denominator.negate();
        }
        BigInteger gcd = numerator.gcd(denominator);
        if (gcd.signum() != 0) {
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
        }
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public static Rational of(long numerator, long denominator) {
        return new Rational(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    /** Exact decimal parsing: "0.3127" -> 3127/10000; no binary float conversion. */
    public static Rational of(String decimal) {
        String s = decimal.trim();
        if (s.contains("/")) {
            String[] parts = s.split("/");
            return new Rational(new BigInteger(parts[0].trim()), new BigInteger(parts[1].trim()));
        }
        if (s.contains(".")) {
            String[] parts = s.split("\\.");
            BigInteger whole = new BigInteger(parts[0].isEmpty() ? "0" : parts[0]);
            String frac = parts.length > 1 ? parts[1] : "";
            BigInteger num = whole.multiply(BigInteger.TEN.pow(frac.length()))
                    .add(frac.isEmpty() ? BigInteger.ZERO : new BigInteger(frac));
            return new Rational(num, BigInteger.TEN.pow(frac.length()));
        }
        return new Rational(new BigInteger(s), BigInteger.ONE);
    }

    public BigInteger numerator() {
        return numerator;
    }

    public BigInteger denominator() {
        return denominator;
    }

    public boolean isPositive() {
        return numerator.signum() > 0;
    }

    public boolean isNonNegative() {
        return numerator.signum() >= 0;
    }

    @Override
    public int compareTo(Rational other) {
        return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Rational other)) {
            return false;
        }
        return numerator.equals(other.numerator) && denominator.equals(other.denominator);
    }

    @Override
    public int hashCode() {
        return 31 * numerator.hashCode() + denominator.hashCode();
    }

    @Override
    public String toString() {
        return numerator + "/" + denominator;
    }
}
