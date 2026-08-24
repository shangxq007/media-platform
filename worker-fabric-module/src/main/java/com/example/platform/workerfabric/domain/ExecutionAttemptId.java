package com.example.platform.workerfabric.domain;

import java.io.Serializable;

/** Stable identity of one backend-neutral execution attempt. */
public record ExecutionAttemptId(String value) implements Serializable {

    public ExecutionAttemptId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ExecutionAttemptId must not be blank");
        }
    }

    public static ExecutionAttemptId of(String value) {
        return new ExecutionAttemptId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
