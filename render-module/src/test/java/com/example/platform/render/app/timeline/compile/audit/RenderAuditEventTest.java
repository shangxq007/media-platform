package com.example.platform.render.app.timeline.compile.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RenderAuditEventTest {

    @Test
    @DisplayName("Event does not contain raw command")
    void noRawCommand() {
        RenderAuditEvent event = RenderAuditEvent.builder()
                .eventType(RenderAuditEventType.PROVIDER_EXECUTION_COMPLETED)
                .projectId("proj-1").timelineRevisionId("rev-1")
                .message("Execution completed").build();
        assertNull(event.sanitizedDetails());
        assertFalse(event.toString().contains("ffmpeg -i"));
    }

    @Test
    @DisplayName("Event does not contain storage internals")
    void noStorageInternals() {
        RenderAuditEvent event = RenderAuditEvent.builder()
                .eventType(RenderAuditEventType.OUTPUT_REGISTRATION_COMPLETED)
                .projectId("proj-1").timelineRevisionId("rev-1")
                .message("Output registered").build();
        String s = event.toString();
        assertFalse(s.contains("bucket"));
        assertFalse(s.contains("objectKey"));
        assertFalse(s.contains("rootPath"));
        assertFalse(s.contains("materializedPath"));
        assertFalse(s.contains("signedUrl"));
    }

    @Test
    @DisplayName("Event does not contain process environment")
    void noProcessEnvironment() {
        RenderAuditEvent event = RenderAuditEvent.of(
                RenderAuditEventType.RENDER_COMPLETED, RenderAuditEventSeverity.INFO,
                "proj-1", "rev-1", "Completed");
        assertFalse(event.toString().contains("processEnvironment"));
    }

    @Test
    @DisplayName("Event has required fields")
    void hasRequiredFields() {
        RenderAuditEvent event = RenderAuditEvent.of(
                RenderAuditEventType.RENDER_REQUEST_RECEIVED, RenderAuditEventSeverity.INFO,
                "proj-1", "rev-1", "Request received");
        assertNotNull(event.eventId());
        assertNotNull(event.occurredAt());
        assertEquals(RenderAuditEventType.RENDER_REQUEST_RECEIVED, event.eventType());
        assertEquals(RenderAuditEventSeverity.INFO, event.severity());
        assertEquals("proj-1", event.projectId());
        assertEquals("rev-1", event.timelineRevisionId());
        assertEquals("Request received", event.message());
    }

    @Test
    @DisplayName("Builder sets all fields correctly")
    void builderSetsFields() {
        RenderAuditEvent event = RenderAuditEvent.builder()
                .eventType(RenderAuditEventType.RENDER_COMPLETED)
                .severity(RenderAuditEventSeverity.INFO)
                .projectId("proj-1").timelineRevisionId("rev-1")
                .renderJobId("rj-1").renderRequestFingerprint("rfp-abc")
                .executionMode("PLAN_BASED").artifactGraphId("ag-1")
                .capabilityGraphId("cg-1").providerBindingPlanId("pbp-1")
                .renderExecutionPlanId("rep-1").providerName("ffmpeg")
                .inputProductIds(List.of("input-1")).outputProductId("output-1")
                .message("Completed").sanitizedDetails("details").build();
        assertEquals("proj-1", event.projectId());
        assertEquals("rj-1", event.renderJobId());
        assertEquals("rfp-abc", event.renderRequestFingerprint());
        assertEquals("PLAN_BASED", event.executionMode());
        assertEquals("ag-1", event.artifactGraphId());
        assertEquals("cg-1", event.capabilityGraphId());
        assertEquals("pbp-1", event.providerBindingPlanId());
        assertEquals("rep-1", event.renderExecutionPlanId());
        assertEquals("ffmpeg", event.providerName());
        assertEquals(List.of("input-1"), event.inputProductIds());
        assertEquals("output-1", event.outputProductId());
    }

    @Test
    @DisplayName("Event IDs are unique")
    void eventIdsAreUnique() {
        RenderAuditEvent e1 = RenderAuditEvent.of(
                RenderAuditEventType.RENDER_COMPLETED, RenderAuditEventSeverity.INFO,
                "proj-1", "rev-1", "msg");
        RenderAuditEvent e2 = RenderAuditEvent.of(
                RenderAuditEventType.RENDER_COMPLETED, RenderAuditEventSeverity.INFO,
                "proj-1", "rev-1", "msg");
        assertNotEquals(e1.eventId(), e2.eventId());
    }

    @Test
    @DisplayName("Fingerprint field stored correctly")
    void fingerprintStoredCorrectly() {
        RenderAuditEvent event = RenderAuditEvent.builder()
                .eventType(RenderAuditEventType.RENDER_DEDUP_CHECKED)
                .renderRequestFingerprint("rfp-test").message("Dedup checked").build();
        assertEquals("rfp-test", event.renderRequestFingerprint());
    }
}
