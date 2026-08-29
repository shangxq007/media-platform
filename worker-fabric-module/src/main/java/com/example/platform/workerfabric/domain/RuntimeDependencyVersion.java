package com.example.platform.workerfabric.domain;

import java.io.Serializable;
import java.util.Arrays;

/** Canonical bounded numeric dependency version used only for conformance matching. */
public record RuntimeDependencyVersion(String value)
        implements Comparable<RuntimeDependencyVersion>, Serializable {

    private static final String NUMERIC_VERSION =
            "(?:0|[1-9][0-9]*)(?:\\.(?:0|[1-9][0-9]*)){0,3}";

    public RuntimeDependencyVersion {
        if (value == null || !value.matches(NUMERIC_VERSION)) {
            throw new IllegalArgumentException("runtime dependency version must be canonical numeric notation");
        }
        int[] components = parse(value);
        int last = components.length - 1;
        while (last > 0 && components[last] == 0) {
            last--;
        }
        StringBuilder canonical = new StringBuilder();
        for (int index = 0; index <= last; index++) {
            if (index > 0) {
                canonical.append('.');
            }
            canonical.append(components[index]);
        }
        value = canonical.toString();
    }

    public static RuntimeDependencyVersion of(String value) {
        return new RuntimeDependencyVersion(value);
    }

    @Override
    public int compareTo(RuntimeDependencyVersion other) {
        int[] left = parse(value);
        int[] right = parse(other.value);
        int length = Math.max(left.length, right.length);
        for (int index = 0; index < length; index++) {
            int comparison = Integer.compare(
                    index < left.length ? left[index] : 0,
                    index < right.length ? right[index] : 0);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return value;
    }

    private static int[] parse(String value) {
        try {
            return Arrays.stream(value.split("\\.")).mapToInt(Integer::parseInt).toArray();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("runtime dependency version component is out of range", exception);
        }
    }
}
