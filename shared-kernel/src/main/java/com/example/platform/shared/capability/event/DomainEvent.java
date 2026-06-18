package com.example.platform.shared.capability.event;

import java.time.Instant;
import java.util.Map;

/**
 * Represents an immutable fact that already happened.
 *
 * <p>DomainEvent is a fact about something that occurred in the system.
 * Events are immutable and should not block the original operation.</p>
 *
 * <p><strong>Contract only:</strong> This defines the event shape.
 * Event bus/runtime is not implemented.</p>
 */
public record DomainEvent(
    String eventType,
    String eventVersion,
    Instant occurredAt,
    String tenantId,
    String aggregateType,
    String aggregateId,
    Map<String, Object> payload
) {
    public DomainEvent {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
        payload = payload != null ? Map.copyOf(payload) : Map.of();
    }

    /**
     * Create a simple event for testing or basic cases.
     */
    public static DomainEvent simple(String eventType, String tenantId, String aggregateType, String aggregateId) {
        return new DomainEvent(
            eventType,
            "1.0.0",
            Instant.now(),
            tenantId,
            aggregateType,
            aggregateId,
            Map.of()
        );
    }
}
