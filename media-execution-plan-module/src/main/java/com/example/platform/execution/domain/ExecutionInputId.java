package com.example.platform.execution.domain;

import java.util.Objects;

/**
 * ROADMAP #21 typed input binding identity (frozen ledger REUSE_AS_CANONICAL).
 * Stable typed identity of an input binding on a logical/physical node.
 */
public record ExecutionInputId(String value) {

    public ExecutionInputId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ExecutionInputId must not be blank");
        }
    }
}
