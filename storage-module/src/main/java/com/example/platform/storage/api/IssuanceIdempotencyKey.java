package com.example.platform.storage.api;

/** Caller-supplied issuance key whose persistence scope is the owning tenant. */
public record IssuanceIdempotencyKey(String value) {

    public IssuanceIdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("issuance idempotency key must not be blank");
        }
    }
}
