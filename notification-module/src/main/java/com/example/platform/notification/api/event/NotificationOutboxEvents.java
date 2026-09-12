package com.example.platform.notification.api.event;
import com.example.platform.notification.api.ingress.NotificationInboundEvent;
import com.example.platform.outbox.api.event.*;
import java.util.List;
import org.springframework.stereotype.Component;

/** Existing inbound event's payload map is notification-defined extension data, not an arbitrary envelope. */
@Component
@org.springframework.modulith.NamedInterface("events")
public final class NotificationOutboxEvents implements OutboxEventCatalog {
    public static final OutboxEventType<NotificationInboundEvent> INBOUND = new OutboxEventType<>(
            "notification.event.published", 1, "NOTIFICATION", NotificationInboundEvent.class, NotificationInboundEvent::subjectId, event -> null);
    @Override public List<OutboxEventType<?>> types() { return List.of(INBOUND); }
}
