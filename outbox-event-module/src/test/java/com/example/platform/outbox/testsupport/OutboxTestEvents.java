package com.example.platform.outbox.testsupport;

import com.example.platform.outbox.api.event.*;
import com.example.platform.outbox.app.OutboxEventRouter;
import com.example.platform.shared.events.ArtifactCreatedEvent;
import com.example.platform.shared.events.RenderJobCreatedEvent;
import java.util.List;
import java.util.Map;

/** Explicit test-only domain contracts, never part of production registration. */
public final class OutboxTestEvents implements OutboxEventCatalog {
    public record OrderCreated(String orderId, String key) {}
    public static final OutboxEventType<OrderCreated> ORDER = new OutboxEventType<>("order.created", 1, "order", OrderCreated.class, OrderCreated::orderId, e -> null);
    public static final OutboxEventType<ArtifactCreatedEvent> ARTIFACT = new OutboxEventType<>("artifact.created", 1, "artifact", ArtifactCreatedEvent.class, ArtifactCreatedEvent::artifactId, e -> null);
    public static final OutboxEventType<RenderJobCreatedEvent> RENDER = new OutboxEventType<>("render.job.created", 1, "render_job", RenderJobCreatedEvent.class, RenderJobCreatedEvent::renderJobId, e -> null);
    @Override public List<OutboxEventType<?>> types() { return List.of(ORDER, ARTIFACT, RENDER); }
    public static OutboxEventRouter router() { return new OutboxEventRouter(List.of(new OutboxTestEvents())); }
    public static OutboxAppend<OrderCreated> order(String id, String value, String key) { return ORDER.append("tenant-test", new OrderCreated(id, value), key); }
    public static Map<String,Object> row(String id) {
        var event = RENDER.append("tenant-test", new RenderJobCreatedEvent("rj-1", "p-1", "ts-1", "default", "ffmpeg"), null);
        return Map.of("id",id,"event_type",event.type().name(),"event_version",1,"aggregate_type","render_job","aggregate_id","rj-1","payload",router().encode(event));
    }
}
