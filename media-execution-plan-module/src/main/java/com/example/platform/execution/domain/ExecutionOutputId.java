package com.example.platform.execution.domain;

import java.io.Serializable;

/**
 * Business identity of an Execution Output declaration within a plan.
 *
 * <p>Immutable strong type scoped to a single plan.
 */
public record ExecutionOutputId(String value) implements Serializable {

    public ExecutionOutputId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ExecutionOutputId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
