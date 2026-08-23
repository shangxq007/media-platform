package com.example.platform.execution.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * ROADMAP #21 typed output declaration identity (frozen ledger
 * REUSE_AS_CANONICAL). Stable typed identity of an output declaration.
 */
public record ExecutionOutputId(String value) implements Serializable {

    public ExecutionOutputId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ExecutionOutputId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
