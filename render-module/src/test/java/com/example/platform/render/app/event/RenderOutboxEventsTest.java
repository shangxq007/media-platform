package com.example.platform.render.app.event;

import com.example.platform.outbox.app.OutboxEventRouter;
import com.example.platform.shared.events.*;
import com.example.platform.render.api.event.*;
import com.example.platform.shared.authorization.ActorType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderOutboxEventsTest {
    @Test void terminalFactRetainsItsExactTimeArtifactAndInitiatorDuringReconstruction() {
        var router = new OutboxEventRouter(List.of(new RenderOutboxEvents()));
        var event = new RenderJobCompletedEvent(new com.example.platform.artifact.app.ArtifactOutputReference(new com.example.platform.artifact.app.ArtifactScope((RenderInitiator.restore(ActorType.USER, "actor", "tenant")).tenantId(),"project","job"),new com.example.platform.shared.identity.ArtifactId("artifact")), Instant.parse("2026-09-12T00:00:00Z"), RenderInitiator.restore(ActorType.USER, "actor", "tenant"));
        var append = RenderOutboxEvents.RENDERJOBCOMPLETEDEVENT.append("tenant", event, "completion:job");
        assertEquals(event, router.decode("render.job.completed", 2, "render_job", "job", router.encode(append)).payload());
        assertThrows(IllegalArgumentException.class, () -> RenderOutboxEvents.RENDERJOBCOMPLETEDEVENT.append("foreign", event, null));
        assertEquals(22, router.size());
    }
}
