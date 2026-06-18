package com.example.platform.shared.capability.event;

import java.util.Map;

/**
 * Represents a subscription to domain events.
 *
 * <p>EventSubscription defines how events should be delivered
 * to targets (automation triggers, webhooks, notifications).</p>
 *
 * <p><strong>Contract only:</strong> This defines the subscription shape.
 * Event bus/runtime is not implemented.</p>
 */
public record EventSubscription(
    String subscriptionId,
    String tenantId,
    String eventType,
    SubscriptionTargetType targetType,
    String targetRef,
    boolean enabled,
    Map<String, Object> filterExpression
) {
    public enum SubscriptionTargetType {
        AUTOMATION_TRIGGER,
        WEBHOOK,
        NOTIFICATION,
        ACTION_REFERENCE
    }

    public EventSubscription {
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new IllegalArgumentException("subscriptionId must not be blank");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (targetType == null) {
            throw new IllegalArgumentException("targetType must not be null");
        }
        if (targetRef == null || targetRef.isBlank()) {
            throw new IllegalArgumentException("targetRef must not be blank");
        }
        filterExpression = filterExpression != null ? Map.copyOf(filterExpression) : Map.of();
    }
}
