package com.example.platform.audit.app;

import com.example.platform.shared.events.ArtifactCreatedEvent;
import com.example.platform.shared.events.RenderJobCompletedEvent;
import com.example.platform.shared.events.RenderJobCreatedEvent;
import com.example.platform.shared.events.RenderJobFailedEvent;
import com.example.platform.shared.events.RenderJobStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuditEventHandler {
    private static final Logger log = LoggerFactory.getLogger(AuditEventHandler.class);

    private final AuditService auditService;

    public AuditEventHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
    public void onRenderJobCreated(RenderJobCreatedEvent event) {
        log.info("AuditEventHandler: recording audit for render job created={}", event.renderJobId());
        auditService.record("SYSTEM", event.projectId(), "RENDER_JOB_CREATED",
                "RENDER_JOB", event.renderJobId(),
                Map.of("projectId", event.projectId(), "profile", event.profile(),
                        "backend", event.primaryBackend()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onRenderJobStatusChanged(RenderJobStatusChangedEvent event) {
        log.info("AuditEventHandler: recording audit for render job status change={}", event.renderJobId());
        auditService.record("SYSTEM", event.projectId(), "RENDER_JOB_STATUS_CHANGED",
                "RENDER_JOB", event.renderJobId(),
                Map.of("oldStatus", event.oldStatus(), "newStatus", event.newStatus()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onRenderJobCompleted(RenderJobCompletedEvent event) {
        log.info("AuditEventHandler: recording audit for render job completed={}", event.renderJobId());
        auditService.record("SYSTEM", event.projectId(), "RENDER_JOB_COMPLETED",
                "RENDER_JOB", event.renderJobId(),
                Map.of("artifactId", event.artifactId(), "storageUri", event.storageUri()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onRenderJobFailed(RenderJobFailedEvent event) {
        log.info("AuditEventHandler: recording audit for render job failed={}", event.renderJobId());
        auditService.record("SYSTEM", event.projectId(), "RENDER_JOB_FAILED",
                "RENDER_JOB", event.renderJobId(),
                Map.of("error", event.error()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onArtifactCreated(ArtifactCreatedEvent event) {
        log.info("AuditEventHandler: recording audit for artifact created={}", event.artifactId());
        auditService.record("SYSTEM", event.projectId(), "ARTIFACT_CREATED",
                "ARTIFACT", event.artifactId(),
                Map.of("renderJobId", event.renderJobId(), "storageUri", event.storageUri()),
                AuditCategory.CONFIG);
    }
}
