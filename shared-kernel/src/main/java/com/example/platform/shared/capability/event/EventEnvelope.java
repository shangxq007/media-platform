package com.example.platform.shared.capability.event;

import java.time.Instant;
import java.util.Map;

/**
 * Envelope for domain events with correlation and causation tracking.
 *
 * <p>EventEnvelope wraps a DomainEvent with metadata for tracking
 * event relationships and idempotency.</p>
 *
 * <p><strong>Contract only:</strong> This defines the event envelope shape.
 * Event bus/runtime is not implemented.</p>
 */
public record EventEnvelope(
    String eventId,
    String eventType,
    String eventVersion,
    String tenantId,
    String sourceModule,
    String correlationId,
    String causationId,
    String idempotencyKey,
    Instant occurredAt,
    Map<String, Object> payload
) {
    public EventEnvelope {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
        payload = payload != null ? Map.copyOf(payload) : Map.of();
    }

    /**
     * Create an envelope from a domain event.
     */
    public static EventEnvelope from(DomainEvent event, String eventId, String sourceModule) {
        return new EventEnvelope(
            eventId,
            event.eventType(),
            event.eventVersion(),
            event.tenantId(),
            sourceModule,
            null,
            null,
            null,
            event.occurredAt(),
            event.payload()
        );
    }
}
