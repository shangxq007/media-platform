package com.example.platform.outbox.subscription;

import java.util.Map;

/**
 * Minimal event descriptor for subscription matching.
 * Decoupled from full PlatformEventEnvelope to avoid coupling.
 */
public record EventDescriptor(
    String eventId,
    String eventType,
    String tenantId,
    String projectId,
    String aggregateType,
    String aggregateId,
    Map<String, Object> payload
) {}
