package com.example.platform.execution.domain;

import java.io.Serializable;

/**
 * Business identity of an Execution Edge (dependency) within a plan.
 *
 * <p>Immutable strong type scoped to a single plan.
 */
public record ExecutionEdgeId(String value) implements Serializable {

    public ExecutionEdgeId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ExecutionEdgeId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
