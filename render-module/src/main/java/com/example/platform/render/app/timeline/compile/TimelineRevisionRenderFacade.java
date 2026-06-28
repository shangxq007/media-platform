package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.app.timeline.TimelineRevisionRenderService;
import com.example.platform.render.app.timeline.compile.audit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Facade that routes TimelineRevision render requests to either the legacy
 * direct FFmpeg path or the plan-based execution path, with deduplication.
 *
 * <p>Internal only — not exposed in public APIs.</p>
 */
@Service
public class TimelineRevisionRenderFacade {

    private static final Logger log = LoggerFactory.getLogger(TimelineRevisionRenderFacade.class);

    private final TimelineRevisionRenderService legacyService;
    private final PlanBasedTimelineRevisionRenderService planBasedService;
    private final RenderDeduplicationService deduplicationService;
    private final TimelineRenderExecutionProperties properties;
    private final RenderAuditRecorder auditRecorder;

    public TimelineRevisionRenderFacade(
            TimelineRevisionRenderService legacyService,
            PlanBasedTimelineRevisionRenderService planBasedService,
            RenderDeduplicationService deduplicationService,
            TimelineRenderExecutionProperties properties,
            RenderAuditRecorder auditRecorder) {
        this.legacyService = legacyService;
        this.planBasedService = planBasedService;
        this.deduplicationService = deduplicationService;
        this.properties = properties;
        this.auditRecorder = auditRecorder;
    }

    /**
     * Render a TimelineRevision using the configured execution path,
     * with deduplication and audit event trail.
     */
    public TimelineRevisionRenderService.RevisionRenderResult render(
            String projectId, String revisionId, String outputProfile) {

        // Audit: request received
        auditRecorder.record(RenderAuditEvent.builder()
                .eventType(RenderAuditEventType.RENDER_REQUEST_RECEIVED)
                .projectId(projectId).timelineRevisionId(revisionId)
                .executionMode(properties.executionMode().name())
                .message("Render request received")
                .build());

        // Dedup check
        RenderDeduplicationDecision dedupDecision = deduplicationService.check(
                projectId, revisionId, outputProfile, properties.executionMode().name());

        // Audit: dedup checked
        auditRecorder.record(RenderAuditEvent.builder()
                .eventType(RenderAuditEventType.RENDER_DEDUP_CHECKED)
                .severity(dedupDecision.isFailed() ? RenderAuditEventSeverity.WARN : RenderAuditEventSeverity.INFO)
                .projectId(projectId).timelineRevisionId(revisionId)
                .renderRequestFingerprint(dedupDecision.fingerprint() != null
                        ? dedupDecision.fingerprint().value() : null)
                .executionMode(properties.executionMode().name())
                .message("Dedup decision: " + dedupDecision.type() + " reason=" + dedupDecision.reason())
                .build());

        if (dedupDecision.shouldReuse()) {
            log.info("Dedup: reusing existing READY product for project={} revision={} profile={}",
                    projectId, revisionId, outputProfile);
            auditRecorder.record(RenderAuditEvent.builder()
                    .eventType(RenderAuditEventType.RENDER_READY_PRODUCT_REUSED)
                    .projectId(projectId).timelineRevisionId(revisionId)
                    .outputProductId(dedupDecision.reusedResult() != null
                            ? dedupDecision.reusedResult().outputProductId() : null)
                    .message("Reusing existing READY product")
                    .build());
            return dedupDecision.reusedResult();
        }

        if (dedupDecision.isFailed()) {
            log.warn("Dedup: lookup failed for project={} revision={}: {}",
                    projectId, revisionId, dedupDecision.message());
            auditRecorder.record(RenderAuditEvent.builder()
                    .eventType(RenderAuditEventType.RENDER_DEDUP_FAILED_CLOSED)
                    .severity(RenderAuditEventSeverity.ERROR)
                    .projectId(projectId).timelineRevisionId(revisionId)
                    .message("Dedup failed closed: " + dedupDecision.message())
                    .build());
            throw new IllegalStateException("Render deduplication failed: " + dedupDecision.message());
        }

        // Audit: new attempt or retry
        RenderAuditEventType attemptType = dedupDecision.reason() == RenderDeduplicationReason.FAILED_PREVIOUS_ATTEMPT
                ? RenderAuditEventType.RENDER_RETRY_AFTER_FAILURE
                : RenderAuditEventType.RENDER_NEW_ATTEMPT_STARTED;
        auditRecorder.record(RenderAuditEvent.builder()
                .eventType(attemptType)
                .projectId(projectId).timelineRevisionId(revisionId)
                .renderRequestFingerprint(dedupDecision.fingerprint().value())
                .executionMode(properties.executionMode().name())
                .message("Starting new render attempt via " + properties.executionMode())
                .build());

        // Proceed with render
        TimelineRevisionRenderService.RevisionRenderResult result;
        try {
            if (properties.isPlanBasedEnabled()) {
                log.info("Rendering via plan-based path: project={} revision={} mode={}",
                        projectId, revisionId, properties.executionMode());
                result = planBasedService.render(projectId, revisionId, outputProfile);
            } else {
                log.info("Rendering via legacy path: project={} revision={} mode={}",
                        projectId, revisionId, properties.executionMode());
                result = legacyService.render(projectId, revisionId, outputProfile);
            }
        } catch (Exception e) {
            auditRecorder.record(RenderAuditEvent.builder()
                    .eventType(RenderAuditEventType.RENDER_FAILED)
                    .severity(RenderAuditEventSeverity.ERROR)
                    .projectId(projectId).timelineRevisionId(revisionId)
                    .message("Render failed: " + sanitizeMessage(e.getMessage()))
                    .build());
            throw e;
        }

        // Audit: completed
        auditRecorder.record(RenderAuditEvent.builder()
                .eventType(RenderAuditEventType.RENDER_COMPLETED)
                .projectId(projectId).timelineRevisionId(revisionId)
                .renderJobId(result.renderJobId())
                .outputProductId(result.outputProductId())
                .inputProductIds(result.inputProductIds())
                .message("Render completed successfully")
                .build());

        return result;
    }

    private String sanitizeMessage(String message) {
        if (message == null) return "unknown error";
        // Remove potential path information
        return message.replaceAll("(/[\\w.-]+)+", "[path]")
                .replaceAll("[A-Za-z]:\\\\[\\w\\\\.-]+", "[path]");
    }

    /**
     * Returns the current execution mode.
     */
    public TimelineRenderExecutionMode getExecutionMode() {
        return properties.executionMode();
    }

    /**
     * Returns true if plan-based execution is enabled.
     */
    public boolean isPlanBasedEnabled() {
        return properties.isPlanBasedEnabled();
    }
}
