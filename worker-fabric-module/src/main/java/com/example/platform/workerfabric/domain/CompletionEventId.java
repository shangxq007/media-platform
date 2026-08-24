package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** Idempotency identity for one normalized platform completion request. */
public record CompletionEventId(String value) {

    public CompletionEventId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("CompletionEventId must not be blank");
        }
    }
}
