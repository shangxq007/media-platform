package com.example.platform.render.app.timeline.compile.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Internal render audit recorder — records lifecycle events to a sink.
 * Fails the audited operation when recording fails; success without required audit is forbidden.
 * Internal only — not exposed in public APIs.
 */
@Component
public class RenderAuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(RenderAuditRecorder.class);

    private final RenderAuditEventSink sink;

    public RenderAuditRecorder(RenderAuditEventSink sink) {
        this.sink = sink;
    }

    /**
     * Record an audit event. Sink failures propagate fail-closed.
     */
    public void record(RenderAuditEvent event) {
        sink.record(event);
        log.debug("Audit event recorded: type={} project={} revision={}",
                event.eventType(), event.projectId(), event.timelineRevisionId());
    }

    /**
     * Get the underlying sink (for testing).
     */
    public RenderAuditEventSink getSink() {
        return sink;
    }
}
