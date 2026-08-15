package com.example.platform.shared.version;

import java.util.Objects;
import java.util.Optional;

/**
 * VERSION_COMPATIBILITY_GOVERNANCE_FOUNDATION_V1 (VCG-1B): typed normalized
 * compatibility range over a comparable version domain.
 *
 * <p>One semantic comparison rule with typed wrappers: numeric comparison only,
 * same version domain only, deterministic normalization. Bounds are inclusive
 * by default; exclusive bounds supported via factories. Lower &gt; upper and
 * empty ranges are rejected.
 *
 * <p>Compatibility is ALWAYS scoped by contract/format identity — numerical
 * equality alone never implies compatibility (timeline.format@2.4 != audio.mix@2.4).
 */
public record VersionRange<T extends Comparable<T>>(
        T lower, boolean lowerInclusive,
        T upper, boolean upperInclusive) {

    public VersionRange {
        Objects.requireNonNull(lower, "lower");
        Objects.requireNonNull(upper, "upper");
        if (lower.compareTo(upper) > 0) {
            throw new IllegalArgumentException("range lower bound must not exceed upper bound");
        }
    }

    public static <T extends Comparable<T>> VersionRange<T> exactly(T version) {
        return new VersionRange<>(version, true, version, true);
    }

    public static <T extends Comparable<T>> VersionRange<T> between(T lower, T upper) {
        return new VersionRange<>(lower, true, upper, true);
    }

    public static <T extends Comparable<T>> VersionRange<T> atLeast(T lower) {
        return new VersionRange<>(lower, true, null, false);
    }

    public static <T extends Comparable<T>> VersionRange<T> upToExclusive(T upper) {
        return new VersionRange<>(null, false, upper, false);
    }

    /** Numeric range containment (inclusive/exclusive aware). */
    public boolean contains(T version) {
        Objects.requireNonNull(version, "version");
        if (lower != null) {
            int c = version.compareTo(lower);
            if (c < 0 || (c == 0 && !lowerInclusive)) return false;
        }
        if (upper != null) {
            int c = version.compareTo(upper);
            if (c > 0 || (c == 0 && !upperInclusive)) return false;
        }
        return true;
    }

    /** Intersection with another range over the same domain; empty when disjoint. */
    public Optional<VersionRange<T>> intersection(VersionRange<T> other) {
        T lo = maxLower(other);
        T hi = minUpper(other);
        if (lo != null && hi != null && lo.compareTo(hi) > 0) {
            return Optional.empty();
        }
        if (lo != null && hi != null && lo.compareTo(hi) == 0
                && (!includes(lo) || !other.includes(lo))) {
            return Optional.empty();
        }
        boolean loIncl = lowerInclusive && other.lowerInclusive;
        boolean hiIncl = upperInclusive && other.upperInclusive;
        return Optional.of(new VersionRange<>(lo, loIncl, hi, hiIncl));
    }

    private boolean includes(T v) {
        return contains(v);
    }

    private T maxLower(VersionRange<T> other) {
        if (lower == null) return other.lower;
        if (other.lower == null) return lower;
        int c = lower.compareTo(other.lower);
        return c >= 0 ? lower : other.lower;
    }

    private T minUpper(VersionRange<T> other) {
        if (upper == null) return other.upper;
        if (other.upper == null) return upper;
        int c = upper.compareTo(other.upper);
        return c <= 0 ? upper : other.upper;
    }

    /** Deterministic external readable form, e.g. {@code >=2.1 <3.0}. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (lower != null) {
            sb.append(lowerInclusive ? ">=" : ">").append(lower);
        }
        if (upper != null) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(upperInclusive ? "<=" : "<").append(upper);
        }
        return sb.toString();
    }
}
