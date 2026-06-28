package com.example.platform.render.app.timeline.compile.audit;

import java.util.List;

/**
 * Query interface for the internal render audit trail.
 * Internal only — not exposed in public APIs.
 */
public class RenderAuditTrail {

    private final RenderAuditEventSink sink;

    public RenderAuditTrail(RenderAuditEventSink sink) {
        this.sink = sink;
    }

    public List<RenderAuditEvent> getEventsForRenderJob(String renderJobId) {
        return sink.findByRenderJobId(renderJobId);
    }

    public List<RenderAuditEvent> getEventsForProject(String projectId) {
        return sink.findByProjectId(projectId);
    }

    public List<RenderAuditEvent> getAllEvents() {
        return sink.findAll();
    }

    public int getEventCount() {
        return sink.findAll().size();
    }

    public boolean hasEventOfType(RenderAuditEventType type) {
        return sink.findAll().stream().anyMatch(e -> e.eventType() == type);
    }

    public List<RenderAuditEvent> getEventsOfType(RenderAuditEventType type) {
        return sink.findAll().stream()
                .filter(e -> e.eventType() == type)
                .toList();
    }
}
