package com.example.platform.audit.app;

import com.example.platform.shared.events.ArtifactCreatedEvent;
import com.example.platform.shared.events.RenderJobCompletedEvent;
import com.example.platform.shared.events.RenderJobCreatedEvent;
import com.example.platform.shared.events.RenderJobFailedEvent;
import com.example.platform.shared.events.RenderJobStatusChangedEvent;
import com.example.platform.shared.events.TimelineMergedEvent;
import com.example.platform.shared.events.TimelineRestoredEvent;
import com.example.platform.shared.events.ReviewApprovedEvent;
import com.example.platform.shared.events.ReviewRejectedEvent;
import com.example.platform.shared.events.ReviewChangesRequestedEvent;
import com.example.platform.shared.events.ReviewCommentAddedEvent;
import com.example.platform.shared.events.ReviewThreadResolvedEvent;
import com.example.platform.shared.events.AssetRegisteredEvent;
import com.example.platform.shared.events.AssetMetadataUpdatedEvent;
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
    public void onRenderJobCreated(RenderJobCreatedEvent event) {
        log.info("AuditEventHandler: recording audit for render job created={}", event.renderJobId());
        auditService.record("SYSTEM", "render-event-handler", "RENDER_JOB_CREATED",
                "RENDER_JOB", event.renderJobId(),
                Map.of("projectId", event.projectId(), "profile", event.profile(),
                        "backend", event.primaryBackend()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onRenderJobStatusChanged(RenderJobStatusChangedEvent event) {
        log.info("AuditEventHandler: recording audit for render job status change={}", event.renderJobId());
        auditService.record("SYSTEM", "render-event-handler", "RENDER_JOB_STATUS_CHANGED",
                "RENDER_JOB", event.renderJobId(),
                Map.of("oldStatus", event.oldStatus(), "newStatus", event.newStatus(),
                        "projectId", event.projectId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onRenderJobCompleted(RenderJobCompletedEvent event) {
        log.info("AuditEventHandler: recording audit for render job completed={}", event.renderJobId());
        auditService.record("SYSTEM", "render-event-handler", "RENDER_JOB_COMPLETED",
                "RENDER_JOB", event.renderJobId(),
                Map.of("artifactId", event.artifactId(), "storageUri", event.storageUri(),
                        "projectId", event.projectId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onRenderJobFailed(RenderJobFailedEvent event) {
        log.info("AuditEventHandler: recording audit for render job failed={}", event.renderJobId());
        auditService.record("SYSTEM", "render-event-handler", "RENDER_JOB_FAILED",
                "RENDER_JOB", event.renderJobId(),
                Map.of("error", event.error(), "projectId", event.projectId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onArtifactCreated(ArtifactCreatedEvent event) {
        log.info("AuditEventHandler: recording audit for artifact created={}", event.artifactId());
        auditService.record("SYSTEM", "render-event-handler", "ARTIFACT_CREATED",
                "ARTIFACT", event.artifactId(),
                Map.of("renderJobId", event.renderJobId(), "storageUri", event.storageUri(),
                        "projectId", event.projectId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onTimelineMerged(TimelineMergedEvent event) {
        log.info("AuditEventHandler: recording audit for timeline merged={}", event.mergeRevisionId());
        auditService.record("SYSTEM", "timeline-event-handler", "TIMELINE_MERGED",
                "TIMELINE", event.mergeRevisionId(),
                Map.of("projectId", event.projectId(), "sourceRevision", event.sourceRevisionId(),
                        "targetRevision", event.targetRevisionId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onTimelineRestored(TimelineRestoredEvent event) {
        log.info("AuditEventHandler: recording audit for timeline restored to={}", event.newRevisionId());
        auditService.record("SYSTEM", "timeline-event-handler", "TIMELINE_RESTORED",
                "TIMELINE", event.newRevisionId(),
                Map.of("projectId", event.projectId(), "restoredFrom", event.restoredFromRevisionId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onReviewApproved(ReviewApprovedEvent event) {
        log.info("AuditEventHandler: recording audit for review approved={}", event.reviewId());
        auditService.record("SYSTEM", "review-event-handler", "REVIEW_APPROVED",
                "REVIEW", event.reviewId(),
                Map.of("reviewerUserId", event.reviewerUserId(), "targetType", event.targetType(),
                        "targetId", event.targetId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onReviewRejected(ReviewRejectedEvent event) {
        log.info("AuditEventHandler: recording audit for review rejected={}", event.reviewId());
        auditService.record("SYSTEM", "review-event-handler", "REVIEW_REJECTED",
                "REVIEW", event.reviewId(),
                Map.of("targetType", event.targetType(), "targetId", event.targetId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onReviewChangesRequested(ReviewChangesRequestedEvent event) {
        log.info("AuditEventHandler: recording audit for review changes requested={}", event.reviewId());
        auditService.record("SYSTEM", "review-event-handler", "REVIEW_CHANGES_REQUESTED",
                "REVIEW", event.reviewId(),
                Map.of("reviewerUserId", event.reviewerUserId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onReviewCommentAdded(ReviewCommentAddedEvent event) {
        log.info("AuditEventHandler: recording audit for review comment={}", event.commentId());
        auditService.record("SYSTEM", "review-event-handler", "REVIEW_COMMENT_ADDED",
                "REVIEW", event.commentId(),
                Map.of("reviewId", event.reviewId(), "authorUserId", event.authorUserId()),
                AuditCategory.CONFIG);
    }

    @EventListener
    public void onReviewThreadResolved(ReviewThreadResolvedEvent event) {
        log.info("AuditEventHandler: recording audit for review thread resolved={}", event.threadId());
        auditService.record("SYSTEM", "review-event-handler", "REVIEW_THREAD_RESOLVED",
                "REVIEW", event.threadId(),
                Map.of("reviewId", event.reviewId()), AuditCategory.CONFIG);
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
