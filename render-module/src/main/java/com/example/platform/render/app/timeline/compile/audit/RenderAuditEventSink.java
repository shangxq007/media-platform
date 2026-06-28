package com.example.platform.render.app.timeline.compile.audit;

import java.util.List;

/**
 * Sink for render audit events.
 * Internal only — pluggable storage backend.
 */
public interface RenderAuditEventSink {
    void record(RenderAuditEvent event);
    List<RenderAuditEvent> findAll();
    List<RenderAuditEvent> findByRenderJobId(String renderJobId);
    List<RenderAuditEvent> findByProjectId(String projectId);
    void clear();
}
