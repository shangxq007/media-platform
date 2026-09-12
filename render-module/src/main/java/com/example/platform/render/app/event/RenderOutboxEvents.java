package com.example.platform.render.app.event;

import com.example.platform.outbox.api.event.*;
import com.example.platform.shared.events.*;
import java.util.List;
import org.springframework.stereotype.Component;

/** Versioned definitions for the existing typed facts published by this owner/consumer. */
@Component
@org.springframework.modulith.NamedInterface("events")
public final class RenderOutboxEvents implements OutboxEventCatalog {
    public static final OutboxEventType<TimelineRevisionCreatedEvent> TIMELINEREVISIONCREATEDEVENT = new OutboxEventType<>("timeline.revision.created", 1, "TIMELINE", TimelineRevisionCreatedEvent.class, TimelineRevisionCreatedEvent::revisionId, event -> null);
    public static final OutboxEventType<TimelineMergedEvent> TIMELINEMERGEDEVENT = new OutboxEventType<>("timeline.merged", 1, "TIMELINE", TimelineMergedEvent.class, TimelineMergedEvent::mergeRevisionId, event -> null);
    public static final OutboxEventType<TimelineRestoredEvent> TIMELINERESTOREDEVENT = new OutboxEventType<>("timeline.restored", 1, "TIMELINE", TimelineRestoredEvent.class, TimelineRestoredEvent::newRevisionId, event -> null);
    public static final OutboxEventType<ReviewCreatedEvent> REVIEWCREATEDEVENT = new OutboxEventType<>("review.created", 1, "REVIEW", ReviewCreatedEvent.class, ReviewCreatedEvent::reviewId, event -> null);
    public static final OutboxEventType<ReviewApprovedEvent> REVIEWAPPROVEDEVENT = new OutboxEventType<>("review.approved", 1, "REVIEW", ReviewApprovedEvent.class, ReviewApprovedEvent::reviewId, event -> null);
    public static final OutboxEventType<ReviewRejectedEvent> REVIEWREJECTEDEVENT = new OutboxEventType<>("review.rejected", 1, "REVIEW", ReviewRejectedEvent.class, ReviewRejectedEvent::reviewId, event -> null);
    public static final OutboxEventType<ReviewChangesRequestedEvent> REVIEWCHANGESREQUESTEDEVENT = new OutboxEventType<>("review.changes_requested", 1, "REVIEW", ReviewChangesRequestedEvent.class, ReviewChangesRequestedEvent::reviewId, event -> null);
    public static final OutboxEventType<ReviewCommentAddedEvent> REVIEWCOMMENTADDEDEVENT = new OutboxEventType<>("review.comment.added", 1, "REVIEW", ReviewCommentAddedEvent.class, ReviewCommentAddedEvent::commentId, event -> null);
    public static final OutboxEventType<ReviewThreadResolvedEvent> REVIEWTHREADRESOLVEDEVENT = new OutboxEventType<>("review.thread.resolved", 1, "REVIEW", ReviewThreadResolvedEvent.class, ReviewThreadResolvedEvent::threadId, event -> null);
    public static final OutboxEventType<AssetRegisteredEvent> ASSETREGISTEREDEVENT = new OutboxEventType<>("asset.registered", 1, "ASSET", AssetRegisteredEvent.class, AssetRegisteredEvent::assetId, event -> null);
    public static final OutboxEventType<AssetMetadataUpdatedEvent> ASSETMETADATAUPDATEDEVENT = new OutboxEventType<>("asset.metadata.updated", 1, "ASSET", AssetMetadataUpdatedEvent.class, AssetMetadataUpdatedEvent::assetId, event -> null);
    public static final OutboxEventType<AssetEnrichedEvent> ASSETENRICHEDEVENT = new OutboxEventType<>("asset.enriched", 1, "ASSET", AssetEnrichedEvent.class, AssetEnrichedEvent::assetId, event -> null);
    public static final OutboxEventType<AssetSubmittedForReviewEvent> ASSETSUBMITTEDFORREVIEWEVENT = new OutboxEventType<>("asset.submitted.review", 1, "ASSET", AssetSubmittedForReviewEvent.class, AssetSubmittedForReviewEvent::assetId, event -> null);
    public static final OutboxEventType<AssetApprovedEvent> ASSETAPPROVEDEVENT = new OutboxEventType<>("asset.approved", 1, "ASSET", AssetApprovedEvent.class, AssetApprovedEvent::assetId, event -> null);
    public static final OutboxEventType<AssetPublishedEvent> ASSETPUBLISHEDEVENT = new OutboxEventType<>("asset.published", 1, "ASSET", AssetPublishedEvent.class, AssetPublishedEvent::assetId, event -> null);
    public static final OutboxEventType<AssetArchivedEvent> ASSETARCHIVEDEVENT = new OutboxEventType<>("asset.archived", 1, "ASSET", AssetArchivedEvent.class, AssetArchivedEvent::assetId, event -> null);
    public static final OutboxEventType<RenderJobCreatedEvent> RENDERJOBCREATEDEVENT = new OutboxEventType<>("render.job.created", 1, "render_job", RenderJobCreatedEvent.class, RenderJobCreatedEvent::renderJobId, event -> null);
    public static final OutboxEventType<RenderJobStatusChangedEvent> RENDERJOBSTATUSCHANGEDEVENT = new OutboxEventType<>("render.job.status.changed", 1, "render_job", RenderJobStatusChangedEvent.class, RenderJobStatusChangedEvent::renderJobId, event -> null);
    public static final OutboxEventType<RenderJobCompletedEvent> RENDERJOBCOMPLETEDEVENT = new OutboxEventType<>("render.job.completed", 1, "render_job", RenderJobCompletedEvent.class, RenderJobCompletedEvent::renderJobId, event -> event.initiator().tenantId());
    public static final OutboxEventType<RenderJobFailedEvent> RENDERJOBFAILEDEVENT = new OutboxEventType<>("render.job.failed", 1, "render_job", RenderJobFailedEvent.class, RenderJobFailedEvent::renderJobId, event -> event.initiator().tenantId());
    public static final OutboxEventType<ArtifactCreatedEvent> ARTIFACTCREATEDEVENT = new OutboxEventType<>("artifact.created", 1, "artifact", ArtifactCreatedEvent.class, ArtifactCreatedEvent::artifactId, event -> null);
    @Override public List<OutboxEventType<?>> types() { return List.of(TIMELINEREVISIONCREATEDEVENT, TIMELINEMERGEDEVENT, TIMELINERESTOREDEVENT, REVIEWCREATEDEVENT, REVIEWAPPROVEDEVENT, REVIEWREJECTEDEVENT, REVIEWCHANGESREQUESTEDEVENT, REVIEWCOMMENTADDEDEVENT, REVIEWTHREADRESOLVEDEVENT, ASSETREGISTEREDEVENT, ASSETMETADATAUPDATEDEVENT, ASSETENRICHEDEVENT, ASSETSUBMITTEDFORREVIEWEVENT, ASSETAPPROVEDEVENT, ASSETPUBLISHEDEVENT, ASSETARCHIVEDEVENT, RENDERJOBCREATEDEVENT, RENDERJOBSTATUSCHANGEDEVENT, RENDERJOBCOMPLETEDEVENT, RENDERJOBFAILEDEVENT, ARTIFACTCREATEDEVENT); }
}
