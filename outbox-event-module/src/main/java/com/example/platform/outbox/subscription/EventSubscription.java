package com.example.platform.outbox.subscription;

import java.util.Map;
import java.util.Optional;

/**
 * Event subscription definition — determines which events are routed to which delivery providers.
 * Immutable value object. No secrets, no raw destination URLs.
 */
public record EventSubscription(
    String id,
    String name,
    String eventTypePattern,
    SubscriberType subscriberType,
    DeliveryProviderType deliveryProviderType,
    DestinationRef destinationRef,
    EventFilter filter,
    String retryPolicyRef,
    boolean enabled,
    int priority,
    Map<String, Object> metadata
) {
    public EventSubscription {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (eventTypePattern == null || eventTypePattern.isBlank()) throw new IllegalArgumentException("eventTypePattern required");
        if (subscriberType == null) throw new IllegalArgumentException("subscriberType required");
        if (deliveryProviderType == null) throw new IllegalArgumentException("deliveryProviderType required");
        if (destinationRef == null) throw new IllegalArgumentException("destinationRef required");
    }
}
