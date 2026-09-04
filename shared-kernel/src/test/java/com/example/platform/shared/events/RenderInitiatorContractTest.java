package com.example.platform.shared.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.authorization.CanonicalActor;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
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
    void eventsPreserveExactInitiatorWithoutAuthorizationOrAudienceData() {
        RenderInitiator initiator = RenderInitiator.from(
                CanonicalActor.user("principal-p1", "tenant-1", Set.of("ADMIN"), "jwt"));
        RenderJobCompletedEvent event = new RenderJobCompletedEvent(
                "rj-1", "project-1", "artifact-1", "storage://artifact-1",
                Instant.parse("2026-08-29T00:00:00Z"), initiator);

        Set<String> snapshotFields = Arrays.stream(initiator.getClass().getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        assertFalse(snapshotFields.contains("roles"));
        assertFalse(snapshotFields.contains("authSource"));
        assertFalse(snapshotFields.contains("email"));
        assertFalse(snapshotFields.contains("subscriber"));

        assertEquals(initiator, event.initiator());
        assertEquals(initiator, new RenderJobFailedEvent(
                "rj-1", "project-1", "failed", Instant.EPOCH, initiator).initiator());
    }
}
