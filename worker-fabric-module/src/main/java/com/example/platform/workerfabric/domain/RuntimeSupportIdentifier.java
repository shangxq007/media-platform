package com.example.platform.workerfabric.domain;

import java.io.Serializable;

/** Stable identifier for one statically installed worker-runtime support surface. */
public record RuntimeSupportIdentifier(String value) implements Comparable<RuntimeSupportIdentifier>, Serializable {

    public RuntimeSupportIdentifier {
        if (value == null || !value.matches("[a-z0-9]+(?:[._-][a-z0-9]+)+")) {
            throw new IllegalArgumentException("runtime support identifier must be stable and normalized");
        }
    }

    public static RuntimeSupportIdentifier of(String value) {
        return new RuntimeSupportIdentifier(value);
    }

    @Override
    public int compareTo(RuntimeSupportIdentifier other) {
        return value.compareTo(other.value);
    }
}
