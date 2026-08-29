package com.example.platform.workerfabric.domain;

import java.io.Serializable;

/** Canonical implementation-local ABI label for an observed dependency. */
public record RuntimeDependencyAbi(String value) implements Serializable {

    private static final String NORMALIZED_ABI = "[a-z0-9]+(?:[._+-][a-z0-9]+)*";

    public RuntimeDependencyAbi {
        if (value == null || !value.matches(NORMALIZED_ABI)) {
            throw new IllegalArgumentException("runtime dependency ABI must be normalized");
        }
    }

    public static RuntimeDependencyAbi of(String value) {
        return new RuntimeDependencyAbi(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
