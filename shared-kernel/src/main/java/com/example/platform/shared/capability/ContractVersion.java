package com.example.platform.shared.capability;

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
 *
 * <p>CAPABILITY_OPERATION_PARAMETER_MODEL / E-2b-1b: moved from
 * {@code extension.domain} to {@code shared.capability} (single authority). This
 * is the <em>capability</em> contract version; the Operation's own version
 * authority remains {@code OperationDefinitionVersion} (operation-module).</p>
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

    /** Parses canonical {@code "major.minor"} only; anything else fails closed (C16-CORR-2). */
    public static ContractVersion parse(String s) {
        Objects.requireNonNull(s, "s");
        String[] parts = s.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("contract version must be major.minor (e.g. 1.0): " + s);
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return new ContractVersion(major, minor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("contract version must be numeric major.minor: " + s, e);
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
