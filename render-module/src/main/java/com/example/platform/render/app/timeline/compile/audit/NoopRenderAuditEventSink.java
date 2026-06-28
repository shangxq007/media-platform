package com.example.platform.render.app.timeline.compile.audit;

import java.util.List;

/**
 * No-op audit event sink — discards all events.
 * Internal only — safe default for production if audit is not needed.
 */
public class NoopRenderAuditEventSink implements RenderAuditEventSink {
    @Override public void record(RenderAuditEvent event) { /* discard */ }
    @Override public List<RenderAuditEvent> findAll() { return List.of(); }
    @Override public List<RenderAuditEvent> findByRenderJobId(String renderJobId) { return List.of(); }
    @Override public List<RenderAuditEvent> findByProjectId(String projectId) { return List.of(); }
    @Override public void clear() { /* no-op */ }
}
