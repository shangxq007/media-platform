package com.example.platform.outbox.subscription;

import java.util.Map;

/**
 * Context for event delivery, carrying metadata without exposing internals.
 */
public record DeliveryContext(
    String eventId,
    String eventType,
    String tenantId,
    String projectId,
    String correlationId,
    String subscriptionId,
    DestinationRef destinationRef,
    String retryPolicyRef,
    Map<String, Object> metadata
) {}
