package com.example.platform.workerfabric.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/** Typed non-secret mismatch reason, optionally scoped to an exact dependency coordinate. */
public record RuntimeDependencyMatchReason(
        RuntimeDependencyMatchReasonCode code,
        Optional<RuntimeDependencyCoordinate> coordinate)
        implements Comparable<RuntimeDependencyMatchReason>, Serializable {

    public RuntimeDependencyMatchReason {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(coordinate, "coordinate");
    }

    public static RuntimeDependencyMatchReason general(RuntimeDependencyMatchReasonCode code) {
        return new RuntimeDependencyMatchReason(code, Optional.empty());
    }

    public static RuntimeDependencyMatchReason forDependency(
            RuntimeDependencyMatchReasonCode code, RuntimeDependencyCoordinate coordinate) {
        return new RuntimeDependencyMatchReason(code, Optional.of(coordinate));
    }

    @Override
    public int compareTo(RuntimeDependencyMatchReason other) {
        int codeComparison = Integer.compare(code.ordinal(), other.code.ordinal());
        if (codeComparison != 0) {
            return codeComparison;
        }
        if (coordinate.isEmpty()) {
            return other.coordinate.isEmpty() ? 0 : -1;
        }
        if (other.coordinate.isEmpty()) {
            return 1;
        }
        return coordinate.orElseThrow().compareTo(other.coordinate.orElseThrow());
    }
}
