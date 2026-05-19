package com.example.platform.compatibility.domain;

/**
 * A semantic schema version.
 *
 * @param major major version (breaking changes)
 * @param minor minor version (additive changes)
 * @param patch patch version (bugfixes)
 */
public record SchemaVersion(int major, int minor, int patch) implements Comparable<SchemaVersion> {
    public static SchemaVersion of(int major, int minor, int patch) {
        return new SchemaVersion(major, minor, patch);
    }

    public static SchemaVersion parse(String version) {
        String[] parts = version.split("\\.");
        if (parts.length < 2) throw new IllegalArgumentException("Invalid version: " + version);
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        return new SchemaVersion(major, minor, patch);
    }

    @Override
    public int compareTo(SchemaVersion other) {
        int cmp = Integer.compare(major, other.major);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(minor, other.minor);
        if (cmp != 0) return cmp;
        return Integer.compare(patch, other.patch);
    }

    public boolean isBefore(SchemaVersion other) {
        return compareTo(other) < 0;
    }

    public boolean isAfter(SchemaVersion other) {
        return compareTo(other) > 0;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
