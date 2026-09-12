package com.example.platform.outbox.app;

import com.example.platform.outbox.api.event.*;
import com.example.platform.outbox.testsupport.OutboxTestEvents;
import com.example.platform.shared.events.ArtifactCreatedEvent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutboxEventRouterTest {
    @Test void exactVersionAndTypedPayloadRoundTripIncludingInstant() {
        var router = OutboxTestEvents.router();
        var event = new ArtifactCreatedEvent("art", "job", "project", Instant.parse("2026-09-12T00:00:00Z"));
        String json = router.encode(OutboxTestEvents.ARTIFACT.append("tenant", event, null));
        assertTrue(json.contains("2026-09-12T00:00:00Z"));
        assertEquals(event, router.decode("artifact.created", 1, "artifact", "art", json).payload());
    }
    @Test void malformedUnknownVersionAndLegacyMarkerAreExplicitlyRejected() {
        var router = OutboxTestEvents.router();
        assertThrows(OutboxEventRouter.InvalidEvent.class, () -> router.decode("order.created", 2, "order", "ord", "{}"));
        assertThrows(OutboxEventRouter.InvalidEvent.class, () -> router.decode("notification.event.published", 1, "generic", "id", "marker"));
        assertThrows(OutboxEventRouter.InvalidEvent.class, () -> router.decode("order.created", 1, "order", "ord", "{\"orderId\":\"ord\",\"key\":\"old\"}"));
        String json = router.encode(OutboxTestEvents.order("ord", "value", null));
        assertThrows(OutboxEventRouter.InvalidEvent.class, () -> router.decode("order.created", 1, "order", "other", json));
        assertThrows(OutboxEventRouter.InvalidEvent.class, () -> router.decode("order.created", 1, "order", "ord", json.replace("\"key\":\"value\"", "\"unregistered\":true")));
    }
    @Test void duplicateRegistrationAndUnregisteredAppendCannotReplaceTheDefiningDomain() {
        assertThrows(IllegalArgumentException.class, () -> new OutboxEventRouter(List.of(new OutboxTestEvents(), new OutboxTestEvents())));
        var forged = new OutboxEventType<>("order.created", 1, "order", OutboxTestEvents.OrderCreated.class, OutboxTestEvents.OrderCreated::orderId, p -> null);
        assertThrows(IllegalArgumentException.class, () -> OutboxTestEvents.router().encode(forged.append("tenant", new OutboxTestEvents.OrderCreated("ord", "value"), null)));
    }
}
