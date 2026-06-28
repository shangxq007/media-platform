package com.example.platform.render.app.timeline.compile.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RenderAuditTrailTest {

    private InMemoryRenderAuditEventSink sink;
    private RenderAuditTrail trail;

    @BeforeEach
    void setUp() {
        sink = new InMemoryRenderAuditEventSink();
        trail = new RenderAuditTrail(sink);
    }

    @Test
    @DisplayName("Trail returns events for render job")
    void returnsEventsForRenderJob() {
        sink.record(event("rj-1", "proj-1"));
        sink.record(event("rj-1", "proj-1"));
        sink.record(event("rj-2", "proj-1"));
        assertEquals(2, trail.getEventsForRenderJob("rj-1").size());
    }

    @Test
    @DisplayName("Trail returns events for project")
    void returnsEventsForProject() {
        sink.record(event("rj-1", "proj-1"));
        sink.record(event("rj-2", "proj-2"));
        assertEquals(1, trail.getEventsForProject("proj-1").size());
    }

    @Test
    @DisplayName("Trail filters by event type")
    void filtersByEventType() {
        sink.record(RenderAuditEvent.of(RenderAuditEventType.RENDER_REQUEST_RECEIVED,
                RenderAuditEventSeverity.INFO, "proj-1", "rev-1", "Request"));
        sink.record(RenderAuditEvent.of(RenderAuditEventType.RENDER_COMPLETED,
                RenderAuditEventSeverity.INFO, "proj-1", "rev-1", "Completed"));
        assertTrue(trail.hasEventOfType(RenderAuditEventType.RENDER_REQUEST_RECEIVED));
        assertTrue(trail.hasEventOfType(RenderAuditEventType.RENDER_COMPLETED));
        assertFalse(trail.hasEventOfType(RenderAuditEventType.RENDER_FAILED));
        assertEquals(1, trail.getEventsOfType(RenderAuditEventType.RENDER_COMPLETED).size());
    }

    @Test
    @DisplayName("Trail counts events")
    void countsEvents() {
        sink.record(event("rj-1", "proj-1"));
        sink.record(event("rj-1", "proj-1"));
        assertEquals(2, trail.getEventCount());
    }

    @Test
    @DisplayName("Trail returns all events")
    void returnsAllEvents() {
        sink.record(event("rj-1", "proj-1"));
        sink.record(event("rj-2", "proj-2"));
        assertEquals(2, trail.getAllEvents().size());
    }

    private RenderAuditEvent event(String renderJobId, String projectId) {
        return RenderAuditEvent.builder()
                .eventType(RenderAuditEventType.RENDER_COMPLETED)
                .projectId(projectId).renderJobId(renderJobId).message("Test event").build();
    }
}
