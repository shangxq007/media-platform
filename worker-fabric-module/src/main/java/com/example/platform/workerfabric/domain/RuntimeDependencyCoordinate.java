package com.example.platform.workerfabric.domain;

import java.io.Serializable;

/** Normalized implementation-local name of one runtime dependency. */
public record RuntimeDependencyCoordinate(String value)
        implements Comparable<RuntimeDependencyCoordinate>, Serializable {

    private static final String NORMALIZED_NAME = "[a-z0-9]+(?:[._-][a-z0-9]+)*";

    public RuntimeDependencyCoordinate {
        if (value == null || !value.matches(NORMALIZED_NAME)) {
            throw new IllegalArgumentException("runtime dependency coordinate must be normalized");
        }
    }

    public static RuntimeDependencyCoordinate of(String value) {
        return new RuntimeDependencyCoordinate(value);
    }

    @Override
    public int compareTo(RuntimeDependencyCoordinate other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
