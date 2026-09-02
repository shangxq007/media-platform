package com.example.platform.shared.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.platform.shared.Jsons;
import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.authorization.CanonicalActor;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RenderInitiatorContractTest {

    @Test
    void authenticatedPrincipalIsSnapshottedWithoutRolesOrAuthProvider() {
        CanonicalActor actor = CanonicalActor.user(
                "principal-p1", "tenant-1", Set.of("ADMIN"), "oauth-provider");

        RenderInitiator initiator = RenderInitiator.from(actor);

        RenderInitiator.Principal principal = assertInstanceOf(
                RenderInitiator.Principal.class, initiator);
        assertEquals("principal-p1", principal.actorId());
        assertEquals(ActorType.USER, principal.actorType());
        assertEquals("tenant-1", principal.tenantId());
    }

    @Test
    void apiKeyAndExplicitSystemRemainDistinct() {
        RenderInitiator apiKey = RenderInitiator.from(
                CanonicalActor.apiKey("api-key-1", "tenant-1", Set.of(), "api-key"));
        RenderInitiator system = RenderInitiator.from(
                CanonicalActor.system("workflow-render-service", "tenant-1"));

        assertInstanceOf(RenderInitiator.Principal.class, apiKey);
        assertEquals(ActorType.API_KEY_PRINCIPAL, apiKey.actorType());
        assertInstanceOf(RenderInitiator.System.class, system);
        assertEquals(ActorType.SYSTEM, system.actorType());
        assertEquals("workflow-render-service", system.actorId());
    }

    @Test
    void tenantlessOrBlankActorsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> RenderInitiator.from(CanonicalActor.system("scheduler", null)));
        assertThrows(IllegalArgumentException.class,
                () -> RenderInitiator.restore(ActorType.USER, " ", "tenant-1"));
        assertThrows(NullPointerException.class, () -> RenderInitiator.from(null));
    }

    @Test
    void jsonAndEventsRoundTripPreserveExactInitiator() {
        RenderInitiator initiator = RenderInitiator.from(
                CanonicalActor.user("principal-p1", "tenant-1", Set.of("ADMIN"), "jwt"));
        RenderJobCompletedEvent event = new RenderJobCompletedEvent(
                "rj-1", "project-1", "artifact-1", "storage://artifact-1",
                Instant.parse("2026-08-29T00:00:00Z"), initiator);

        String json = Jsons.toJson(event);
        assertFalse(json.contains("ADMIN"));
        assertFalse(json.contains("authSource"));
        assertFalse(json.contains("email"));
        assertFalse(json.contains("subscriber"));
        RenderJobCompletedEvent restored = Jsons.fromJson(json, RenderJobCompletedEvent.class);

        assertEquals(initiator, restored.initiator());
        assertEquals(event, restored);
        assertEquals(initiator, new RenderJobFailedEvent(
                "rj-1", "project-1", "failed", Instant.EPOCH, initiator).initiator());
    }
}
