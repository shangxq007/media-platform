package com.example.platform.commerce.domain;

import java.time.Instant;

/** Explicit versioned command for the stable product lifecycle. */
public record LifecycleProductCommand(CatalogActor actor, String productId, long expectedVersion,
        ProductLifecycleState targetState, String idempotencyKey, String source, String reason,
        String traceId, Instant occurredAt) {
    public LifecycleProductCommand {
        if (actor == null || !actor.globalCatalog() || productId == null || productId.isBlank() || expectedVersion < 1
                || targetState == null || idempotencyKey == null || idempotencyKey.isBlank()
                || source == null || source.isBlank() || reason == null || reason.isBlank()
                || traceId == null || traceId.isBlank() || occurredAt == null) {
            throw new IllegalArgumentException("complete global product lifecycle command is required");
        }
    }
}
