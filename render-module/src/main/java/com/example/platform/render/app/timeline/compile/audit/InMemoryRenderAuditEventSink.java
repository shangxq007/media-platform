package com.example.platform.render.app.timeline.compile.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory audit event sink for testing and development.
 * Internal only.
 */
public class InMemoryRenderAuditEventSink implements RenderAuditEventSink {

    private final List<RenderAuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void record(RenderAuditEvent event) {
        events.add(event);
    }

    @Override
    public List<RenderAuditEvent> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    @Override
    public List<RenderAuditEvent> findByRenderJobId(String renderJobId) {
        return events.stream()
                .filter(e -> renderJobId.equals(e.renderJobId()))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<RenderAuditEvent> findByProjectId(String projectId) {
        return events.stream()
                .filter(e -> projectId.equals(e.projectId()))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public void clear() {
        events.clear();
    }
}
