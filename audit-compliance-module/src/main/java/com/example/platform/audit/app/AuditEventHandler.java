package com.example.platform.audit.app;

import com.example.platform.artifact.api.event.ArtifactCreatedEvent;
import com.example.platform.render.api.event.RenderJobCompletedEvent;
import com.example.platform.render.api.event.RenderJobCreatedEvent;
import com.example.platform.render.api.event.RenderJobFailedEvent;
import com.example.platform.render.api.event.RenderJobStatusChangedEvent;
import com.example.platform.timeline.api.event.TimelineMergedEvent;
import com.example.platform.timeline.api.event.TimelineRestoredEvent;
import com.example.platform.timeline.api.event.TimelineReviewApprovedEvent;
import com.example.platform.timeline.api.event.TimelineReviewRejectedEvent;
import com.example.platform.timeline.api.event.TimelineReviewChangesRequestedEvent;
import com.example.platform.timeline.api.event.TimelineReviewCommentAddedEvent;
import com.example.platform.timeline.api.event.TimelineReviewThreadResolvedEvent;
import com.example.platform.artifact.api.event.AssetRegisteredEvent;
import com.example.platform.artifact.api.event.AssetMetadataUpdatedEvent;
import com.example.platform.shared.events.AssetApprovedEvent;
import com.example.platform.shared.events.AssetPublishedEvent;
import com.example.platform.shared.events.AssetArchivedEvent;
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
    public void onProviderRuntimeBound(com.example.platform.render.api.binding.ProviderRuntimeBindingResolvedEvent event) {
        auditService.recordFact(event.factKey(),"SYSTEM","provider-runtime-event-handler","PROVIDER_RUNTIME_BOUND",
            "RENDER_JOB",event.renderJobId(),Map.of("projectId",event.projectId(),"provider",event.provider().providerId(),"resolutionId",event.resolutionId(),"tenantId",event.tenantId()),AuditCategory.CONFIG);
    }

    @EventListener
    public void onRenderJobCreated(RenderJobCreatedEvent event) {
        log.info("AuditEventHandler: recording audit for render job created={}", event.renderJobId());
        auditService.recordFact(event.factKey(), "SYSTEM", "render-event-handler", "RENDER_JOB_CREATED",
                "RENDER_JOB", event.renderJobId(),
                Map.of("projectId", event.projectId(), "profile", event.profile(), "tenantId", event.tenantId(), "initiatorId", event.initiator().actorId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onRenderJobStatusChanged(RenderJobStatusChangedEvent event) {
        log.info("AuditEventHandler: recording audit for render job status change={}", event.renderJobId());
        auditService.recordFact(event.factKey(), "SYSTEM", "render-event-handler", "RENDER_JOB_STATUS_CHANGED",
                "RENDER_JOB", event.renderJobId(),
                Map.of("oldStatus", event.oldStatus().name(), "newStatus", event.newStatus().name(),
                        "projectId", event.projectId(), "tenantId", event.tenantId(), "initiatorId", event.initiator().actorId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onRenderJobCompleted(RenderJobCompletedEvent event) {
        log.info("AuditEventHandler: recording audit for render job completed={}", event.renderJobId());
        auditService.recordFact(event.factKey(), "SYSTEM", "render-event-handler", "RENDER_JOB_COMPLETED",
                "RENDER_JOB", event.renderJobId(),
                Map.of("artifactId", event.result().artifactId().value(),
                        "projectId", event.projectId(), "tenantId", event.tenantId(), "initiatorId", event.initiator().actorId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onRenderJobFailed(RenderJobFailedEvent event) {
        log.info("AuditEventHandler: recording audit for render job failed={}", event.renderJobId());
        auditService.recordFact(event.factKey(), "SYSTEM", "render-event-handler", event.outcome()==com.example.platform.render.domain.RenderJobStatus.REJECTED ? "RENDER_JOB_REJECTED" : "RENDER_JOB_FAILED",
                "RENDER_JOB", event.renderJobId(),
                Map.of("error", event.reason().description(), "reason", event.reason().name(), "projectId", event.projectId(), "outcome", event.outcome().name(), "tenantId", event.tenantId(), "initiatorId", event.initiator().actorId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onArtifactCreated(ArtifactCreatedEvent event) {
        log.info("AuditEventHandler: recording audit for artifact created={}", event.artifactId());
        auditService.recordFact(event.factKey(), "SYSTEM", "artifact-event-handler", "ARTIFACT_CREATED",
                "ARTIFACT", event.artifactId(),
                Map.of("renderJobId", event.renderJobId(), "projectId", event.projectId(), "tenantId", event.tenantId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onTimelineMerged(TimelineMergedEvent event) {
        log.info("AuditEventHandler: recording audit for timeline merged={}", event.mergeRevisionId());
        auditService.recordFact(event.factKey(), "SYSTEM", "timeline-event-handler", "TIMELINE_MERGED",
                "TIMELINE", event.mergeRevisionId(),
                Map.of("tenantId",event.tenantId(),"projectId",event.projectId(), "sourceRevision", event.sourceRevisionId(),
                        "targetRevision", event.targetRevisionId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onTimelineRestored(TimelineRestoredEvent event) {
        log.info("AuditEventHandler: recording audit for timeline restored to={}", event.newRevisionId());
        auditService.recordFact(event.factKey(), "SYSTEM", "timeline-event-handler", "TIMELINE_RESTORED",
                "TIMELINE", event.newRevisionId(),
                Map.of("tenantId",event.tenantId(),"projectId",event.projectId(), "restoredFrom", event.restoredFromRevisionId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onReviewApproved(TimelineReviewApprovedEvent event) {
        log.info("AuditEventHandler: recording audit for review approved={}", event.reviewId());
        auditService.recordFact(event.factKey(), "SYSTEM", "review-event-handler", "REVIEW_APPROVED",
                "REVIEW", event.reviewId(),
                Map.of("tenantId",event.tenantId(),"projectId",event.projectId(),"reviewerUserId", event.reviewerUserId(), "targetType", "TIMELINE",
                        "targetId", event.revisionId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onReviewRejected(TimelineReviewRejectedEvent event) {
        log.info("AuditEventHandler: recording audit for review rejected={}", event.reviewId());
        auditService.recordFact(event.factKey(), "SYSTEM", "review-event-handler", "REVIEW_REJECTED",
                "REVIEW", event.reviewId(),
                Map.of("tenantId",event.tenantId(),"projectId",event.projectId(),"targetType", "TIMELINE", "targetId", event.revisionId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onReviewChangesRequested(TimelineReviewChangesRequestedEvent event) {
        log.info("AuditEventHandler: recording audit for review changes requested={}", event.reviewId());
        auditService.recordFact(event.factKey(), "SYSTEM", "review-event-handler", "REVIEW_CHANGES_REQUESTED",
                "REVIEW", event.reviewId(),
                Map.of("tenantId",event.tenantId(),"projectId",event.projectId(),"reviewerUserId", event.reviewerUserId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onReviewCommentAdded(TimelineReviewCommentAddedEvent event) {
        log.info("AuditEventHandler: recording audit for review comment={}", event.commentId());
        auditService.recordFact(event.factKey(), "SYSTEM", "review-event-handler", "REVIEW_COMMENT_ADDED",
                "REVIEW", event.commentId(),
                Map.of("tenantId",event.tenantId(),"projectId",event.projectId(),"reviewId", event.reviewId(), "authorUserId", event.authorUserId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onReviewThreadResolved(TimelineReviewThreadResolvedEvent event) {
        log.info("AuditEventHandler: recording audit for review thread resolved={}", event.threadId());
        auditService.recordFact(event.factKey(), "SYSTEM", "review-event-handler", "REVIEW_THREAD_RESOLVED",
                "REVIEW", event.threadId(),
                Map.of("tenantId",event.tenantId(),"projectId",event.projectId(),"reviewId", event.reviewId()), AuditCategory.CONFIG);
    }

    @EventListener
    public void onAssetRegistered(AssetRegisteredEvent event) {
        log.info("AuditEventHandler: recording audit for asset registered={}", event.assetId());
        auditService.record("SYSTEM", "asset-event-handler", "ASSET_REGISTERED",
                "ASSET", event.assetId(),
                Map.of("projectId", event.projectId(), "assetType", event.assetType()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onAssetMetadataUpdated(AssetMetadataUpdatedEvent event) {
        log.info("AuditEventHandler: recording audit for asset metadata updated={}", event.assetId());
        auditService.record("SYSTEM", "asset-event-handler", "ASSET_METADATA_UPDATED",
                "ASSET", event.assetId(),
                Map.of("projectId", event.projectId()), AuditCategory.CONFIG);
    }

    @EventListener
    public void onAssetApproved(AssetApprovedEvent event) {
        log.info("AuditEventHandler: recording audit for asset approved={}", event.assetId());
        auditService.record("SYSTEM", "asset-event-handler", "ASSET_APPROVED",
                "ASSET", event.assetId(),
                Map.of("projectId", event.projectId(), "reviewId", event.reviewId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onAssetPublished(AssetPublishedEvent event) {
        log.info("AuditEventHandler: recording audit for asset published={}", event.assetId());
        auditService.record("SYSTEM", "asset-event-handler", "ASSET_PUBLISHED",
                "ASSET", event.assetId(),
                Map.of("projectId", event.projectId(), "assetType", event.assetType()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onAssetArchived(AssetArchivedEvent event) {
        log.info("AuditEventHandler: recording audit for asset archived={}", event.assetId());
        auditService.record("SYSTEM", "asset-event-handler", "ASSET_ARCHIVED",
                "ASSET", event.assetId(),
                Map.of("projectId", event.projectId()), AuditCategory.CONFIG);
    }
}
