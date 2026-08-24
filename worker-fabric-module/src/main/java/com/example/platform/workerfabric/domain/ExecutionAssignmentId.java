package com.example.platform.workerfabric.domain;

import java.io.Serializable;

/** Stable identity of one concrete runtime placement. */
public record ExecutionAssignmentId(String value) implements Serializable {

    public ExecutionAssignmentId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ExecutionAssignmentId must not be blank");
        }
    }

    public static ExecutionAssignmentId of(String value) {
        return new ExecutionAssignmentId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
