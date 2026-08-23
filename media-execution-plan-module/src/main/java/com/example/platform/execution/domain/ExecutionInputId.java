package com.example.platform.execution.domain;

import java.io.Serializable;

/**
 * Business identity of an Execution Input binding within a plan.
 *
 * <p>Immutable strong type scoped to a single plan.
 */
public record ExecutionInputId(String value) implements Serializable {

    public ExecutionInputId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ExecutionInputId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
