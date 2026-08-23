package com.example.platform.execution.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * ROADMAP #21 typed logical/physical node identity (frozen ledger
 * REUSE_AS_CANONICAL). Stable typed identity of an execution node.
 */
public record ExecutionStepId(String value) implements Serializable {

    public ExecutionStepId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ExecutionStepId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
