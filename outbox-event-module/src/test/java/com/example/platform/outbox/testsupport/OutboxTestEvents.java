package com.example.platform.outbox.testsupport;

import com.example.platform.outbox.api.event.*;
import com.example.platform.outbox.app.OutboxEventRouter;
import java.util.List;
import java.util.Map;

/** Explicit test-only domain contracts, never part of production registration. */
public final class OutboxTestEvents implements OutboxEventCatalog {
    public record FixtureArtifactCreated(String artifactId, String renderJobId, String projectId, java.time.Instant createdAt) {}
    public record FixtureCreated(String renderJobId,String projectId,String snapshotId,String profile) {}
    public record OrderCreated(String orderId, String key) {}
    public static final OutboxEventType<OrderCreated> ORDER = new OutboxEventType<>("order.created", 1, "order", OrderCreated.class, OrderCreated::orderId, e -> null);
    public static final OutboxEventType<FixtureArtifactCreated> ARTIFACT = new OutboxEventType<>("artifact.created", 1, "artifact", FixtureArtifactCreated.class, FixtureArtifactCreated::artifactId, e -> null);
    public static final OutboxEventType<FixtureCreated> SAMPLE = new OutboxEventType<>("fixture.created", 1, "fixture_job", FixtureCreated.class, FixtureCreated::renderJobId, e -> null);
    @Override public List<OutboxEventType<?>> types() { return List.of(ORDER, ARTIFACT, SAMPLE); }
    public static OutboxEventRouter router() { return new OutboxEventRouter(List.of(new OutboxTestEvents())); }
    public static OutboxAppend<OrderCreated> order(String id, String value, String key) { return ORDER.append("tenant-test", new OrderCreated(id, value), key); }
    public static Map<String,Object> row(String id) {
        var event = SAMPLE.append("tenant-test", new FixtureCreated("rj-1", "p-1", "ts-1", "default"), null);
        return Map.of("id",id,"event_type",event.type().name(),"event_version",1,"aggregate_type","fixture_job","aggregate_id","rj-1","payload",router().encode(event));
    }
}
