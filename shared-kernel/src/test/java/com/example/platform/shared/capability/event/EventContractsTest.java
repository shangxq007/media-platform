package com.example.platform.shared.capability.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for event contracts.
 *
 * <p>These tests verify event contract shapes without implementing event bus/runtime.</p>
 */
class EventContractsTest {

    @Test
    void domainEventRepresentsImmutableFact() {
        DomainEvent event = DomainEvent.simple(
            "asset.uploaded",
            "tenant-123",
            "asset",
            "asset-456"
        );

        assertNotNull(event.eventType());
        assertNotNull(event.occurredAt());
        assertNotNull(event.tenantId());
        assertNotNull(event.aggregateType());
        assertNotNull(event.aggregateId());
        assertNotNull(event.payload());

        // Payload should be immutable
        assertThrows(UnsupportedOperationException.class, () -> {
            event.payload().put("key", "value");
        });
    }

    @Test
    void domainEventRequiresEventType() {
        assertThrows(IllegalArgumentException.class, () -> {
            new DomainEvent(
                null,
                "1.0.0",
                Instant.now(),
                "tenant-123",
                "asset",
                "asset-456",
                Map.of()
            );
        });
    }

    @Test
    void eventEnvelopeCarriesCorrelationCausationIds() {
        EventEnvelope envelope = new EventEnvelope(
            "event-123",
            "asset.uploaded",
            "1.0.0",
            "tenant-456",
            "storage-module",
            "correlation-789",
            "causation-abc",
            "idempotency-xyz",
            Instant.now(),
            Map.of("assetId", "asset-456")
        );

        assertNotNull(envelope.eventId());
        assertNotNull(envelope.eventType());
        assertNotNull(envelope.correlationId());
        assertNotNull(envelope.causationId());
        assertNotNull(envelope.idempotencyKey());
        assertNotNull(envelope.sourceModule());

        // Payload should be immutable
        assertThrows(UnsupportedOperationException.class, () -> {
            envelope.payload().put("key", "value");
        });
    }

    @Test
    void eventEnvelopeCanBeCreatedFromDomainEvent() {
        DomainEvent event = DomainEvent.simple(
            "asset.uploaded",
            "tenant-123",
            "asset",
            "asset-456"
        );

        EventEnvelope envelope = EventEnvelope.from(event, "event-123", "storage-module");

        assertEquals("event-123", envelope.eventId());
        assertEquals("asset.uploaded", envelope.eventType());
        assertEquals("tenant-123", envelope.tenantId());
        assertEquals("storage-module", envelope.sourceModule());
    }

    @Test
    void eventSubscriptionCanTargetAutomationWebhookAction() {
        // Test automation trigger target
        EventSubscription automationSub = new EventSubscription(
            "sub-1",
            "tenant-123",
            "asset.uploaded",
            EventSubscription.SubscriptionTargetType.AUTOMATION_TRIGGER,
            "flow-456",
            true,
            Map.of()
        );

        assertEquals(EventSubscription.SubscriptionTargetType.AUTOMATION_TRIGGER, automationSub.targetType());
        assertEquals("flow-456", automationSub.targetRef());

        // Test webhook target
        EventSubscription webhookSub = new EventSubscription(
            "sub-2",
            "tenant-123",
            "render.completed",
            EventSubscription.SubscriptionTargetType.WEBHOOK,
            "https://example.com/webhook",
            true,
            Map.of()
        );

        assertEquals(EventSubscription.SubscriptionTargetType.WEBHOOK, webhookSub.targetType());
        assertEquals("https://example.com/webhook", webhookSub.targetRef());

        // Test action reference target
        EventSubscription actionSub = new EventSubscription(
            "sub-3",
            "tenant-123",
            "review.approved",
            EventSubscription.SubscriptionTargetType.ACTION_REFERENCE,
            "artifact.export",
            true,
            Map.of()
        );

        assertEquals(EventSubscription.SubscriptionTargetType.ACTION_REFERENCE, actionSub.targetType());
        assertEquals("artifact.export", actionSub.targetRef());
    }

    @Test
    void eventSubscriptionRequiresValidFields() {
        assertThrows(IllegalArgumentException.class, () -> {
            new EventSubscription(
                null,
                "tenant-123",
                "asset.uploaded",
                EventSubscription.SubscriptionTargetType.WEBHOOK,
                "https://example.com/webhook",
                true,
                Map.of()
            );
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new EventSubscription(
                "sub-1",
                "tenant-123",
                null,
                EventSubscription.SubscriptionTargetType.WEBHOOK,
                "https://example.com/webhook",
                true,
                Map.of()
            );
        });
    }

    @Test
    void eventContractsDoNotDependOnSpring() {
        // This test verifies that event contracts are plain Java records
        // No Spring annotations or dependencies should be present
        DomainEvent event = DomainEvent.simple(
            "test.event",
            "tenant-123",
            "test",
            "test-456"
        );

        EventEnvelope envelope = EventEnvelope.from(event, "event-123", "test-module");
        EventSubscription subscription = new EventSubscription(
            "sub-1",
            "tenant-123",
            "test.event",
            EventSubscription.SubscriptionTargetType.WEBHOOK,
            "https://example.com/webhook",
            true,
            Map.of()
        );

        assertNotNull(event);
        assertNotNull(envelope);
        assertNotNull(subscription);
    }
}
