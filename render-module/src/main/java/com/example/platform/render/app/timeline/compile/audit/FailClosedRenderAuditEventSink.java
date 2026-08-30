package com.example.platform.render.app.timeline.compile.audit;

import java.util.List;

/** Prevents audited render work from succeeding while durable audit storage is absent. */
public final class FailClosedRenderAuditEventSink implements RenderAuditEventSink {

    @Override public void record(RenderAuditEvent event) { throw unavailable(); }
    @Override public List<RenderAuditEvent> findAll() { throw unavailable(); }
    @Override public List<RenderAuditEvent> findByRenderJobId(String renderJobId) { throw unavailable(); }
    @Override public List<RenderAuditEvent> findByProjectId(String projectId) { throw unavailable(); }
    @Override public void clear() { throw unavailable(); }

    private static RenderAuditUnavailableException unavailable() {
        return new RenderAuditUnavailableException();
    }
}
