package com.example.platform.shared.version;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VERSION_COMPATIBILITY_GOVERNANCE_FOUNDATION_V1 (VCG-1): canonical schema/format
 * version.
 *
 * <p>Syntax: {@code EPOCH.RELEASE} (e.g. {@code 1.0}, {@code 2.4}). No PATCH
 * component. Used for canonical formats (e.g. Timeline canonical serialization)
 * and is a DIFFERENT compatibility space from contract versions — compatibility
 * is always scoped by contract/format identity (timeline.format@2.4 != audio.mix@2.4).
 */
public record CanonicalFormatVersion(int epoch, int release) implements Comparable<CanonicalFormatVersion> {

    private static final Pattern PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)$");

    public CanonicalFormatVersion {
        if (epoch < 0 || release < 0) {
            throw new IllegalArgumentException("format version parts must be >= 0");
        }
    }

    public static CanonicalFormatVersion of(int epoch, int release) {
        return new CanonicalFormatVersion(epoch, release);
    }

    /** Parses canonical {@code E.R}; single-segment or PATCH forms fail closed. */
    public static CanonicalFormatVersion parse(String s) {
        Objects.requireNonNull(s, "s");
        Matcher m = PATTERN.matcher(s.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("format version must be EPOCH.RELEASE (e.g. 1.0): " + s);
        }
        try {
            return new CanonicalFormatVersion(
                    Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("format version must be numeric E.R: " + s, e);
        }
    }

    @Override
    public int compareTo(CanonicalFormatVersion other) {
        int c = Integer.compare(epoch, other.epoch);
        return c != 0 ? c : Integer.compare(release, other.release);
    }

    @Override
    public String toString() {
        return epoch + "." + release;
    }
}
