package com.example.platform.workerfabric.domain;

import java.io.Serializable;
import java.util.Objects;

/** Exact version or bounded half-open compatibility range; never a package resolver request. */
public record RuntimeDependencyVersionConstraint(
        Kind kind,
        RuntimeDependencyVersion lowerBound,
        RuntimeDependencyVersion upperBound)
        implements Serializable {

    public enum Kind {
        EXACT,
        RANGE
    }

    public RuntimeDependencyVersionConstraint {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(lowerBound, "lowerBound");
        if (kind == Kind.EXACT && upperBound != null) {
            throw new IllegalArgumentException("exact constraint must not have an upper bound");
        }
        if (kind == Kind.RANGE) {
            Objects.requireNonNull(upperBound, "upperBound");
            if (lowerBound.compareTo(upperBound) >= 0) {
                throw new IllegalArgumentException("range must have a non-empty ascending bound");
            }
        }
    }

    public static RuntimeDependencyVersionConstraint exact(RuntimeDependencyVersion version) {
        return new RuntimeDependencyVersionConstraint(Kind.EXACT, version, null);
    }

    public static RuntimeDependencyVersionConstraint range(
            RuntimeDependencyVersion minimumInclusive,
            RuntimeDependencyVersion maximumExclusive) {
        return new RuntimeDependencyVersionConstraint(Kind.RANGE, minimumInclusive, maximumExclusive);
    }

    public boolean matches(RuntimeDependencyVersion candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (kind == Kind.EXACT) {
            return lowerBound.compareTo(candidate) == 0;
        }
        return lowerBound.compareTo(candidate) <= 0 && candidate.compareTo(upperBound) < 0;
    }
}
