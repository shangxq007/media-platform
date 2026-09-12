package com.example.platform.config;

import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.notification.api.ingress.NotificationEventPublisher;
import com.example.platform.notification.api.ingress.NotificationInboundEvent;
import com.example.platform.notification.api.event.NotificationOutboxEvents;
import com.example.platform.shared.web.TenantGuard;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Sole durable adapter for the Notification-owned typed ingress. */
@Component
public final class OutboxBackedNotificationEventPublisher implements NotificationEventPublisher {
    private final OutboxEventService outbox;
    public OutboxBackedNotificationEventPublisher(OutboxEventService outbox) { this.outbox = outbox; }

    @Override public void publish(NotificationInboundEvent event, String idempotencyKey) {
        Objects.requireNonNull(event, "Notification event is required");
        String tenant = TenantGuard.requireTenantId();
        if (idempotencyKey != null && idempotencyKey.isBlank())
            throw new IllegalArgumentException("Notification idempotency key must not be blank");
        outbox.append(NotificationOutboxEvents.INBOUND.append(tenant, event, idempotencyKey));
    }
}
