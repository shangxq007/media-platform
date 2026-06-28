package com.example.platform.render.app.timeline.compile.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Internal render audit recorder — records lifecycle events to a sink.
 * Never fails the render because audit recording failed.
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
     * Record an audit event. Never throws.
     */
    public void record(RenderAuditEvent event) {
        try {
            sink.record(event);
            log.debug("Audit event recorded: type={} project={} revision={}",
                    event.eventType(), event.projectId(), event.timelineRevisionId());
        } catch (Exception e) {
            log.warn("Failed to record audit event: type={} error={}",
                    event.eventType(), e.getMessage());
            // Never fail the render because of audit
        }
    }

    /**
     * Get the underlying sink (for testing).
     */
    public RenderAuditEventSink getSink() {
        return sink;
    }
}
