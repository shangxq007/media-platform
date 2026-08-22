package com.example.platform.execution.domain;

import java.util.Objects;

/**
 * ROADMAP #21 typed output declaration identity (frozen ledger
 * REUSE_AS_CANONICAL). Stable typed identity of an output declaration.
 */
public record ExecutionOutputId(String value) {

    public ExecutionOutputId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ExecutionOutputId must not be blank");
        }
    }
}
