package com.example.platform.render.app.event;

import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.shared.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Unified domain event publisher for Timeline, Review, and Asset domains.
 * All events are written to outbox_events for reliable delivery.
 */
@Service
public class TimelineReviewEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TimelineReviewEventPublisher.class);
    private final OutboxEventService outboxEventService;

    public TimelineReviewEventPublisher(OutboxEventService outboxEventService) {
        this.outboxEventService = outboxEventService;
    }

    public void publish(TimelineRevisionCreatedEvent event) {
        outboxEventService.appendEvent("TIMELINE", event.revisionId(),
                "timeline.revision.created", 1, event);
        log.debug("Outbox: TimelineRevisionCreatedEvent rev={}", event.revisionId());
    }

    public void publish(TimelineMergedEvent event) {
        outboxEventService.appendEvent("TIMELINE", event.mergeRevisionId(),
                "timeline.merged", 1, event);
        log.info("Outbox: TimelineMergedEvent merge={}", event.mergeRevisionId());
    }

    public void publish(TimelineRestoredEvent event) {
        outboxEventService.appendEvent("TIMELINE", event.newRevisionId(),
                "timeline.restored", 1, event);
        log.info("Outbox: TimelineRestoredEvent new={}", event.newRevisionId());
    }

    public void publish(ReviewCreatedEvent event) {
        outboxEventService.appendEvent("REVIEW", event.reviewId(),
                "review.created", 1, event);
        log.debug("Outbox: ReviewCreatedEvent review={}", event.reviewId());
    }

    public void publish(ReviewApprovedEvent event) {
        outboxEventService.appendEvent("REVIEW", event.reviewId(),
                "review.approved", 1, event);
        log.info("Outbox: ReviewApprovedEvent review={}", event.reviewId());
    }

    public void publish(ReviewRejectedEvent event) {
        outboxEventService.appendEvent("REVIEW", event.reviewId(),
                "review.rejected", 1, event);
        log.info("Outbox: ReviewRejectedEvent review={}", event.reviewId());
    }

    public void publish(ReviewChangesRequestedEvent event) {
        outboxEventService.appendEvent("REVIEW", event.reviewId(),
                "review.changes_requested", 1, event);
        log.info("Outbox: ReviewChangesRequestedEvent review={}", event.reviewId());
    }

    public void publish(ReviewCommentAddedEvent event) {
        outboxEventService.appendEvent("REVIEW", event.commentId(),
                "review.comment.added", 1, event);
        log.debug("Outbox: ReviewCommentAddedEvent comment={}", event.commentId());
    }

    public void publish(ReviewThreadResolvedEvent event) {
        outboxEventService.appendEvent("REVIEW", event.threadId(),
                "review.thread.resolved", 1, event);
        log.debug("Outbox: ReviewThreadResolvedEvent thread={}", event.threadId());
    }

    public void publish(AssetRegisteredEvent event) {
        outboxEventService.appendEvent("ASSET", event.assetId(),
                "asset.registered", 1, event);
        log.info("Outbox: AssetRegisteredEvent asset={}", event.assetId());
    }

    public void publish(AssetMetadataUpdatedEvent event) {
        outboxEventService.appendEvent("ASSET", event.assetId(),
                "asset.metadata.updated", 1, event);
        log.debug("Outbox: AssetMetadataUpdatedEvent asset={}", event.assetId());
    }

    public void publish(AssetEnrichedEvent event) {
        outboxEventService.appendEvent("ASSET", event.assetId(),
                "asset.enriched", 1, event);
        log.info("Outbox: AssetEnrichedEvent asset={}", event.assetId());
    }

    public void publish(AssetSubmittedForReviewEvent event) {
        outboxEventService.appendEvent("ASSET", event.assetId(),
                "asset.submitted.review", 1, event);
        log.info("Outbox: AssetSubmittedForReviewEvent asset={}", event.assetId());
    }

    public void publish(AssetApprovedEvent event) {
        outboxEventService.appendEvent("ASSET", event.assetId(),
                "asset.approved", 1, event);
        log.info("Outbox: AssetApprovedEvent asset={}", event.assetId());
    }

    public void publish(AssetPublishedEvent event) {
        outboxEventService.appendEvent("ASSET", event.assetId(),
                "asset.published", 1, event);
        log.info("Outbox: AssetPublishedEvent asset={}", event.assetId());
    }

    public void publish(AssetArchivedEvent event) {
        outboxEventService.appendEvent("ASSET", event.assetId(),
                "asset.archived", 1, event);
        log.info("Outbox: AssetArchivedEvent asset={}", event.assetId());
    }
}
