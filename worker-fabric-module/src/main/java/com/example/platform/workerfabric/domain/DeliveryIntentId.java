package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** Idempotency identity of an outbox delivery intent. */
public record DeliveryIntentId(String value) {

    public DeliveryIntentId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("DeliveryIntentId must not be blank");
        }
    }
}
