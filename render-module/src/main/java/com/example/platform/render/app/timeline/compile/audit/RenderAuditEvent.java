package com.example.platform.render.app.timeline.compile.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Internal render audit event — immutable record of a render lifecycle moment.
 * Internal only — not exposed in public APIs.
 * Does not include raw commands, storage paths, secrets, or credentials.
 */
public record RenderAuditEvent(
        String eventId,
        Instant occurredAt,
        RenderAuditEventType eventType,
        RenderAuditEventSeverity severity,
        String projectId,
        String timelineRevisionId,
        String renderJobId,
        String renderRequestFingerprint,
        String executionMode,
        String artifactGraphId,
        String capabilityGraphId,
        String providerBindingPlanId,
        String renderExecutionPlanId,
        String providerName,
        List<String> inputProductIds,
        String outputProductId,
        String message,
        String sanitizedDetails) {

    public static RenderAuditEvent of(RenderAuditEventType type, RenderAuditEventSeverity severity,
                                       String projectId, String timelineRevisionId, String message) {
        return new RenderAuditEvent(UUID.randomUUID().toString(), Instant.now(),
                type, severity, projectId, timelineRevisionId,
                null, null, null, null, null, null, null, null, null, null, message, null);
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String eventId = UUID.randomUUID().toString();
        private Instant occurredAt = Instant.now();
        private RenderAuditEventType eventType;
        private RenderAuditEventSeverity severity = RenderAuditEventSeverity.INFO;
        private String projectId, timelineRevisionId, renderJobId, renderRequestFingerprint;
        private String executionMode, artifactGraphId, capabilityGraphId;
        private String providerBindingPlanId, renderExecutionPlanId, providerName;
        private List<String> inputProductIds;
        private String outputProductId, message, sanitizedDetails;

        public Builder eventType(RenderAuditEventType v) { this.eventType = v; return this; }
        public Builder severity(RenderAuditEventSeverity v) { this.severity = v; return this; }
        public Builder projectId(String v) { this.projectId = v; return this; }
        public Builder timelineRevisionId(String v) { this.timelineRevisionId = v; return this; }
        public Builder renderJobId(String v) { this.renderJobId = v; return this; }
        public Builder renderRequestFingerprint(String v) { this.renderRequestFingerprint = v; return this; }
        public Builder executionMode(String v) { this.executionMode = v; return this; }
        public Builder artifactGraphId(String v) { this.artifactGraphId = v; return this; }
        public Builder capabilityGraphId(String v) { this.capabilityGraphId = v; return this; }
        public Builder providerBindingPlanId(String v) { this.providerBindingPlanId = v; return this; }
        public Builder renderExecutionPlanId(String v) { this.renderExecutionPlanId = v; return this; }
        public Builder providerName(String v) { this.providerName = v; return this; }
        public Builder inputProductIds(List<String> v) { this.inputProductIds = v; return this; }
        public Builder outputProductId(String v) { this.outputProductId = v; return this; }
        public Builder message(String v) { this.message = v; return this; }
        public Builder sanitizedDetails(String v) { this.sanitizedDetails = v; return this; }

        public RenderAuditEvent build() {
            return new RenderAuditEvent(eventId, occurredAt, eventType, severity,
                    projectId, timelineRevisionId, renderJobId, renderRequestFingerprint,
                    executionMode, artifactGraphId, capabilityGraphId,
                    providerBindingPlanId, renderExecutionPlanId,
                    providerName, inputProductIds, outputProductId, message, sanitizedDetails);
        }
    }
}
