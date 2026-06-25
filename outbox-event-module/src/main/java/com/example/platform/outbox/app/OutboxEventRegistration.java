package com.example.platform.outbox.app;

import com.example.platform.shared.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

/**
 * Registers all 22 outbox event types with the OutboxEventRouter.
 *
 * <p>New event types are added here — no dispatcher code changes needed.
 * This replaces the hardcoded switch statement in OutboxEventDispatcher.</p>
 */
@Configuration
public class OutboxEventRegistration {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventRegistration.class);
    private final OutboxEventRouter router;

    public OutboxEventRegistration(OutboxEventRouter router) {
        this.router = router;
    }

    @PostConstruct
    public void registerAll() {
        // Render domain (5)
        router.register("render.job.created", RenderJobCreatedEvent.class);
        router.register("render.job.status.changed", RenderJobStatusChangedEvent.class);
        router.register("render.job.completed", RenderJobCompletedEvent.class);
        router.register("render.job.failed", RenderJobFailedEvent.class);
        router.register("artifact.created", ArtifactCreatedEvent.class);

        // Timeline domain (3)
        router.register("timeline.revision.created", TimelineRevisionCreatedEvent.class);
        router.register("timeline.merged", TimelineMergedEvent.class);
        router.register("timeline.restored", TimelineRestoredEvent.class);

        // Review domain (6)
        router.register("review.created", ReviewCreatedEvent.class);
        router.register("review.approved", ReviewApprovedEvent.class);
        router.register("review.rejected", ReviewRejectedEvent.class);
        router.register("review.changes_requested", ReviewChangesRequestedEvent.class);
        router.register("review.comment.added", ReviewCommentAddedEvent.class);
        router.register("review.thread.resolved", ReviewThreadResolvedEvent.class);

        // Asset domain (7)
        router.register("asset.registered", AssetRegisteredEvent.class);
        router.register("asset.metadata.updated", AssetMetadataUpdatedEvent.class);
        router.register("asset.enriched", AssetEnrichedEvent.class);
        router.register("asset.submitted.review", AssetSubmittedForReviewEvent.class);
        router.register("asset.approved", AssetApprovedEvent.class);
        router.register("asset.published", AssetPublishedEvent.class);
        router.register("asset.archived", AssetArchivedEvent.class);

        log.info("Registered {} outbox event routes across 4 domains", router.size());
    }
}
