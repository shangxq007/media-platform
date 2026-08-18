package com.example.platform.fonttext.typography;

import java.math.BigInteger;
import java.util.Objects;

/** ROADMAP_19 (C5/C27): exact deterministic rational for all canonical typography numerics. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class FontRational {

    private final BigInteger numerator;
    private final BigInteger denominator;

    @com.fasterxml.jackson.annotation.JsonCreator
public FontRational(@com.fasterxml.jackson.annotation.JsonProperty("numerator") BigInteger numerator, @com.fasterxml.jackson.annotation.JsonProperty("denominator") BigInteger denominator) {
        Objects.requireNonNull(numerator, "numerator");
        Objects.requireNonNull(denominator, "denominator");
        if (denominator.signum() == 0) {
            throw new IllegalArgumentException("zero denominator");
        }
        if (denominator.signum() < 0) {
            numerator = numerator.negate();
            denominator = denominator.negate();
        }
        BigInteger g = numerator.gcd(denominator);
        if (g.signum() != 0) {
            numerator = numerator.divide(g);
            denominator = denominator.divide(g);
        }
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public static FontRational of(long numerator, long denominator) {
        return new FontRational(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    public static FontRational whole(long value) {
        return new FontRational(BigInteger.valueOf(value), BigInteger.ONE);
    }

    public BigInteger numerator() { return numerator; }
    public BigInteger denominator() { return denominator; }

    @Override
    public boolean equals(Object o) {
        return o instanceof FontRational r && numerator.equals(r.numerator) && denominator.equals(r.denominator);
    }

    @Override
    public int hashCode() { return Objects.hash(numerator, denominator); }

    @Override
    public String toString() { return numerator + "/" + denominator; }
}
