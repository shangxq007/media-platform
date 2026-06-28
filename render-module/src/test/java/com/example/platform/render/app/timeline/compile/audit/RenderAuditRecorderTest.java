package com.example.platform.render.app.timeline.compile.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RenderAuditRecorderTest {

    private InMemoryRenderAuditEventSink sink;
    private RenderAuditRecorder recorder;

    @BeforeEach
    void setUp() {
        sink = new InMemoryRenderAuditEventSink();
        recorder = new RenderAuditRecorder(sink);
    }

    @Test
    @DisplayName("Recorder stores events in sink")
    void recordsEvents() {
        recorder.record(RenderAuditEvent.of(
                RenderAuditEventType.RENDER_REQUEST_RECEIVED, RenderAuditEventSeverity.INFO,
                "proj-1", "rev-1", "Request received"));
        assertEquals(1, sink.findAll().size());
        assertEquals(RenderAuditEventType.RENDER_REQUEST_RECEIVED, sink.findAll().get(0).eventType());
    }

    @Test
    @DisplayName("Recorder preserves event order")
    void preservesOrder() {
        recorder.record(RenderAuditEvent.of(RenderAuditEventType.RENDER_REQUEST_RECEIVED,
                RenderAuditEventSeverity.INFO, "proj-1", "rev-1", "First"));
        recorder.record(RenderAuditEvent.of(RenderAuditEventType.RENDER_DEDUP_CHECKED,
                RenderAuditEventSeverity.INFO, "proj-1", "rev-1", "Second"));
        recorder.record(RenderAuditEvent.of(RenderAuditEventType.RENDER_COMPLETED,
                RenderAuditEventSeverity.INFO, "proj-1", "rev-1", "Third"));
        List<RenderAuditEvent> events = sink.findAll();
        assertEquals(3, events.size());
        assertEquals("First", events.get(0).message());
        assertEquals("Second", events.get(1).message());
        assertEquals("Third", events.get(2).message());
    }

    @Test
    @DisplayName("Recorder never fails render on sink error")
    void neverFailsRender() {
        RenderAuditEventSink failingSink = new RenderAuditEventSink() {
            @Override public void record(RenderAuditEvent event) { throw new RuntimeException("Sink error"); }
            @Override public List<RenderAuditEvent> findAll() { return List.of(); }
            @Override public List<RenderAuditEvent> findByRenderJobId(String id) { return List.of(); }
            @Override public List<RenderAuditEvent> findByProjectId(String id) { return List.of(); }
            @Override public void clear() {}
        };
        RenderAuditRecorder failingRecorder = new RenderAuditRecorder(failingSink);
        assertDoesNotThrow(() -> failingRecorder.record(RenderAuditEvent.of(
                RenderAuditEventType.RENDER_COMPLETED, RenderAuditEventSeverity.INFO,
                "proj-1", "rev-1", "Should not fail")));
    }

    @Test
    @DisplayName("Noop sink discards events")
    void noopSinkDiscards() {
        NoopRenderAuditEventSink noop = new NoopRenderAuditEventSink();
        noop.record(RenderAuditEvent.of(RenderAuditEventType.RENDER_COMPLETED,
                RenderAuditEventSeverity.INFO, "proj-1", "rev-1", "Discarded"));
        assertTrue(noop.findAll().isEmpty());
    }

    @Test
    @DisplayName("Recorder exposes sink for testing")
    void exposesSinkForTesting() {
        assertSame(sink, recorder.getSink());
    }
}
