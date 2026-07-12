package com.example.platform.outbox.subscription;

/**
 * Result of matching an event against a subscription.
 */
public record EventRouteMatch(
    EventSubscription subscription,
    DeliveryProviderType deliveryProviderType,
    DestinationRef destinationRef,
    String retryPolicyRef,
    int priority,
    String matchReason
) {}
