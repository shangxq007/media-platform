package com.example.platform.execution.domain;

import java.io.Serializable;

/**
 * Business identity of an Execution Step within a plan.
 *
 * <p>Immutable strong type scoped to a single plan.
 */
public record ExecutionStepId(String value) implements Serializable {

    public ExecutionStepId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ExecutionStepId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
