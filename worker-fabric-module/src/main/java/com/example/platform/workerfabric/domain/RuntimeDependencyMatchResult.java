package com.example.platform.workerfabric.domain;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Immutable fail-closed result from pure runtime dependency matching. */
public record RuntimeDependencyMatchResult(
        RuntimeDependencyMatchStatus status,
        List<RuntimeDependencyMatchReason> reasons)
        implements Serializable {

    public RuntimeDependencyMatchResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reasons, "reasons");
        reasons = List.copyOf(reasons);
        boolean hasUnknown = reasons.stream().anyMatch(reason -> reason.code().unknownEvidence());
        if ((status == RuntimeDependencyMatchStatus.CAN_MATCH) != reasons.isEmpty()) {
            throw new IllegalArgumentException("CAN_MATCH must have no reasons and failures must have reasons");
        }
        if ((status == RuntimeDependencyMatchStatus.UNKNOWN_FAIL_CLOSED) != hasUnknown) {
            throw new IllegalArgumentException("unknown status must match unknown evidence reasons");
        }
    }

    public boolean canMatch() {
        return status == RuntimeDependencyMatchStatus.CAN_MATCH;
    }
}
