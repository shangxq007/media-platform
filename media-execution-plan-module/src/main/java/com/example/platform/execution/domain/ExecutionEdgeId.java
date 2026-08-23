package com.example.platform.execution.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * ROADMAP #21 typed logical dependency edge identity (frozen ledger
 * REUSE_AS_CANONICAL). Stable typed identity of a logical dependency edge.
 */
public record ExecutionEdgeId(String value) implements Serializable {

    public ExecutionEdgeId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ExecutionEdgeId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
