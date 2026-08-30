package com.example.platform.shared;

import com.example.platform.shared.events.ArtifactCreatedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * K2-02 regression: the production-reachable outbox path
 * (artifact.created -> OutboxEventService -> Jsons.toJson) serializes
 * {@link ArtifactCreatedEvent}, which carries a {@link java.time.Instant}.
 *
 * <p>Baseline defect (proven empirically): shared-kernel {@code Jsons}
 * ObjectMapper had no JSR-310 module, so any Instant payload threw
 * {@code com.fasterxml.jackson.databind.exc.InvalidDefinitionException}
 * at runtime — meaning the artifact.created outbox INSERT could never
 * complete for the real payload shape.</p>
 *
 * <p>This test exercises the actual reachable event type (not a bare
 * {@code ObjectMapper} probe) and requires an ISO-8601 java.time wire
 * representation consistent with the platform's Spring-configured
 * serialization convention.</p>
 */
class JsonsJavaTimeSerializationTest {

    @Test
    void artifactCreatedEventWithInstantRoundTripsThroughJsons() {
        Instant createdAt = Instant.parse("2026-08-13T03:16:09Z");
        ArtifactCreatedEvent event =
                new ArtifactCreatedEvent("art-1", "job-1", "proj-1", createdAt);

        String json = Jsons.toJson(event); // must NOT throw on the reachable payload

        assertFalse(json.isEmpty());
        // ISO-8601 wire representation (platform convention), not epoch-decimal
        assertEquals("\"2026-08-13T03:16:09Z\"", json.substring(json.indexOf("createdAt") + 11, json.indexOf('}')));

        ArtifactCreatedEvent decoded = Jsons.fromJson(json, ArtifactCreatedEvent.class);
        assertEquals(event, decoded);
    }

    @Test
    void instantValueInMapSerializesAndDeserializes() {
        java.util.Map<String, Object> payload = java.util.Map.of(
                "tenantId", "t1",
                "occurredAt", Instant.parse("2026-08-13T11:00:35Z"));

        String json = Jsons.toJson(payload); // must NOT throw
        java.util.Map<?, ?> decoded = Jsons.fromJson(json, java.util.Map.class);
        assertEquals("t1", decoded.get("tenantId"));
    }
}
