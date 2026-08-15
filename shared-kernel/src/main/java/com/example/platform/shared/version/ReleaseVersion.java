package com.example.platform.shared.version;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VERSION_COMPATIBILITY_GOVERNANCE_FOUNDATION_V1 (VCG-1): canonical platform
 * software release version.
 *
 * <p>Syntax: {@code EPOCH.RELEASE.PATCH} (e.g. {@code 2.4.0}, {@code 3.0.0}).
 * Semantic intent:
 * <ul>
 *   <li>PATCH — implementation/bug/security fix without intentionally changing
 *       canonical semantic contract;</li>
 *   <li>RELEASE — compatible feature/contract evolution;</li>
 *   <li>EPOCH — incompatible compatibility boundary.</li>
 * </ul>
 * ReleaseVersion is DISTINCT from ContractVersion / CanonicalFormatVersion
 * (E.R, no PATCH) and from revision identities and build identity
 * (RELEASE_VERSION_IS_NOT_DATA_VERSION_IS_NOT_REVISION_ID_V1).
 */
public record ReleaseVersion(int epoch, int release, int patch) implements Comparable<ReleaseVersion> {

    private static final Pattern PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");

    public ReleaseVersion {
        if (epoch < 0 || release < 0 || patch < 0) {
            throw new IllegalArgumentException("release version parts must be >= 0");
        }
    }

    public static ReleaseVersion of(int epoch, int release, int patch) {
        return new ReleaseVersion(epoch, release, patch);
    }

    /** Parses canonical {@code E.R.P}; anything else fails closed. */
    public static ReleaseVersion parse(String s) {
        Objects.requireNonNull(s, "s");
        Matcher m = PATTERN.matcher(s.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("release version must be EPOCH.RELEASE.PATCH (e.g. 2.4.0): " + s);
        }
        try {
            return new ReleaseVersion(
                    Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("release version must be numeric E.R.P: " + s, e);
        }
    }

    @Override
    public int compareTo(ReleaseVersion other) {
        int c = Integer.compare(epoch, other.epoch);
        if (c != 0) return c;
        c = Integer.compare(release, other.release);
        return c != 0 ? c : Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return epoch + "." + release + "." + patch;
    }
}
