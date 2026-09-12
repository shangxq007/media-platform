package com.example.platform.config;

import com.example.platform.notification.api.ingress.NotificationInboundEvent;
import com.example.platform.notification.api.event.NotificationOutboxEvents;
import com.example.platform.outbox.api.event.OutboxAppend;
import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.shared.web.TenantContext;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxNotificationCompositionTest {
    @Test void existingInboundPublisherUsesItsPublishedDomainRecord() {
        OutboxEventService outbox = mock(OutboxEventService.class);
        var publisher = new OutboxBackedNotificationEventPublisher(outbox);
        var event = new NotificationInboundEvent("test.event", "subject", Map.of("boundedField", "value"));
        try {
            TenantContext.set("tenant");
            publisher.publish(event, "notification-key");
            ArgumentCaptor<OutboxAppend<?>> append = ArgumentCaptor.forClass(OutboxAppend.class);
            verify(outbox).append(append.capture());
            assertEquals(NotificationOutboxEvents.INBOUND, append.getValue().type());
            assertEquals(event, append.getValue().payload());
            var router = new com.example.platform.outbox.app.OutboxEventRouter(java.util.List.of(new NotificationOutboxEvents()));
            assertEquals(event, router.decode("notification.event.published", 1, "NOTIFICATION", "subject", router.encode(append.getValue())).payload());
            assertEquals("notification-key", append.getValue().idempotencyKey());
            assertThrows(IllegalArgumentException.class, () -> publisher.publish(event, " "));
            verifyNoMoreInteractions(outbox);
        } finally { TenantContext.clear(); }
    }
}
