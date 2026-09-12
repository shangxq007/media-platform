package com.example.platform.notification.api.ingress;

/** Notification-owned durable ingress. Acceptance is not provider delivery success. */
public interface NotificationEventPublisher {
    /**
     * Accept within the caller's current tenant and transaction. A non-null key identifies
     * one logical notification in that scope; null creates an independent event.
     * Repeated keys use Outbox's existing pending/failed refresh and processed deduplication.
     */
    void publish(NotificationInboundEvent event, String idempotencyKey);
}
