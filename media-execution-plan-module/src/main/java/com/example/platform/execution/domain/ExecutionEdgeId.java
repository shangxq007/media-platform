package com.example.platform.execution.domain;

import java.util.Objects;

/**
 * ROADMAP #21 typed logical dependency edge identity (frozen ledger
 * REUSE_AS_CANONICAL). Stable typed identity of a logical dependency edge.
 */
public record ExecutionEdgeId(String value) {

    public ExecutionEdgeId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ExecutionEdgeId must not be blank");
        }
    }
}
