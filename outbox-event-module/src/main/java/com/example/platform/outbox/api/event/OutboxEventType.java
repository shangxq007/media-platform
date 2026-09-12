package com.example.platform.outbox.api.event;

import java.util.Objects;
import java.util.function.Function;

/** A defining domain registers each supported payload type/version and its identity projection. */
public record OutboxEventType<T extends Record>(String name, int version, String aggregateType,
        Class<T> payloadType, Function<T, String> aggregateId, Function<T, String> payloadTenant) {
    public OutboxEventType {
        if (name == null || name.isBlank() || version < 1 || aggregateType == null || aggregateType.isBlank())
            throw new IllegalArgumentException("Explicit event type, version and aggregate type required");
        Objects.requireNonNull(payloadType); Objects.requireNonNull(aggregateId); Objects.requireNonNull(payloadTenant);
        if (!payloadType.isRecord()) throw new IllegalArgumentException("Typed record payload required");
    }
    public OutboxAppend<T> append(String tenantId, T payload, String idempotencyKey) {
        return new OutboxAppend<>(this, tenantId, payload, idempotencyKey);
    }
}
