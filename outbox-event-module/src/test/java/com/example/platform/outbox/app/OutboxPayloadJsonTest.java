package com.example.platform.outbox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.shared.events.ArtifactCreatedEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutboxPayloadJsonTest {

    @Test
    void artifactCreatedEventWithInstantUsesExactIso8601Payload() {
        ArtifactCreatedEvent event = new ArtifactCreatedEvent(
                "art-1", "job-1", "proj-1", Instant.parse("2026-08-13T03:16:09Z"));

        assertEquals(
                "{\"artifactId\":\"art-1\",\"renderJobId\":\"job-1\",\"projectId\":\"proj-1\",\"createdAt\":\"2026-08-13T03:16:09Z\"}",
                OutboxPayloadJson.toJson(event));
    }

    @Test
    void selfReferentialPayloadFailsClosed() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("self", payload);

        assertThrows(IllegalStateException.class, () -> OutboxPayloadJson.toJson(payload));
    }
}
