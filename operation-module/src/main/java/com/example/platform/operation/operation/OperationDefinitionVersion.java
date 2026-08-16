package com.example.platform.operation.operation;

import java.util.Objects;

/**
 * Version of the semantic contract represented by an OperationDefinition.
 *
 * <p>Owned by operation-module (GCR-1 §13). Replaces the historical dependency
 * on {@code extension.domain.ContractVersion}: Operations must not depend on
 * the extension domain. Typed, immutable, validated, with deterministic
 * equality and a canonical major.minor representation.</p>
 */
public record OperationDefinitionVersion(int major, int minor) {

    public OperationDefinitionVersion {
        if (major < 0 || minor < 0) {
            throw new IllegalArgumentException(
                    "OperationDefinitionVersion components must be non-negative: " + major + "." + minor);
        }
    }

    public static OperationDefinitionVersion of(int major, int minor) {
        return new OperationDefinitionVersion(major, minor);
    }

    public static final OperationDefinitionVersion V1_0 = new OperationDefinitionVersion(1, 0);

    /** Canonical representation: {@code "major.minor"} (e.g. {@code "1.0"}). */
    @Override
    public String toString() {
        return major + "." + minor;
    }

    /** Deterministic equality on the typed components. */
    @Override
    public boolean equals(Object o) {
        return o instanceof OperationDefinitionVersion v && v.major == major && v.minor == minor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor);
    }
}
