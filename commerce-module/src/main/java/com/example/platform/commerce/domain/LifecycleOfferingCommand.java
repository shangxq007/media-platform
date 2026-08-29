package com.example.platform.commerce.domain;

import java.time.Instant;

public record LifecycleOfferingCommand(CatalogActor actor, String offeringId, long expectedVersion,
        OfferingLifecycleState targetState, String idempotencyKey, String source, String reason,
        String traceId, Instant occurredAt) {
    public LifecycleOfferingCommand {
        if (actor == null || offeringId == null || offeringId.isBlank() || expectedVersion < 1
                || targetState == null || idempotencyKey == null || idempotencyKey.isBlank()
                || source == null || source.isBlank() || reason == null || reason.isBlank()
                || traceId == null || traceId.isBlank() || occurredAt == null) {
            throw new IllegalArgumentException("complete lifecycle command is required");
        }
    }
}
