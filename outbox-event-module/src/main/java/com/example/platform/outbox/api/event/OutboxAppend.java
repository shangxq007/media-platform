package com.example.platform.outbox.api.event;

import java.util.Objects;

/** Typed durable append intent. Payload meaning and identity projection belong to its domain. */
public record OutboxAppend<T extends Record>(OutboxEventType<T> type, String tenantId, T payload, String idempotencyKey) {
    public OutboxAppend {
        Objects.requireNonNull(type); Objects.requireNonNull(payload);
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("Outbox tenant scope is required");
        if (!type.payloadType().isInstance(payload)) throw new IllegalArgumentException("Payload type mismatch");
        String id = type.aggregateId().apply(payload);
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Aggregate identity required");
        String payloadTenant = type.payloadTenant().apply(payload);
        if (payloadTenant != null && !tenantId.equals(payloadTenant)) throw new IllegalArgumentException("Payload tenant mismatch");
    }
    public String aggregateId() { return type.aggregateId().apply(payload); }
}
