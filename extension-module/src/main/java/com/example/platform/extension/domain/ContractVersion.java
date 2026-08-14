package com.example.platform.extension.domain;

import java.util.Objects;

/**
 * #16 (C2/C12): typed capability contract version.
 *
 * <p>The capability CONTRACT version is independent of plugin version,
 * implementation version, platform API version, handled-object schema version
 * and provider runtime version. Compatibility is explicit: two contract
 * versions are compatible when the major segment matches and the requirement's
 * range includes the provider's contract version. Version comparison is
 * numeric (never string-lexicographic).
 */
public record ContractVersion(int major, int minor) {

    public ContractVersion {
        if (major < 0 || minor < 0) {
            throw new IllegalArgumentException("contract version parts must be >= 0");
        }
    }

    public static ContractVersion of(int major, int minor) {
        return new ContractVersion(major, minor);
    }

    /** Parses {@code "major.minor"} or legacy single-segment {@code "major"} (= major.0). */
    public static ContractVersion parse(String s) {
        Objects.requireNonNull(s, "s");
        String[] parts = s.split("\\.");
        if (parts.length < 1 || parts.length > 2) {
            throw new IllegalArgumentException("contract version must be major or major.minor: " + s);
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length == 2 ? Integer.parseInt(parts[1]) : 0;
            return new ContractVersion(major, minor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("contract version must be numeric: " + s, e);
        }
    }

    /** True when this version is compatible with {@code requirement} (C12: explicit range). */
    public boolean compatibleWith(ContractVersionRange range) {
        Objects.requireNonNull(range, "range");
        return this.major == range.min().major()
                && compareTo(range.min()) >= 0
                && compareTo(range.max()) <= 0;
    }

    public int compareTo(ContractVersion other) {
        int cmp = Integer.compare(this.major, other.major);
        return cmp != 0 ? cmp : Integer.compare(this.minor, other.minor);
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
