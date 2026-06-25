package com.example.platform.outbox.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.shared.events.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboxEventRegistrationTest {

    private OutboxEventRouter router;

    @BeforeEach
    void setUp() {
        router = new OutboxEventRouter();
        new OutboxEventRegistration(router).registerAll();
    }

    @Test
    void shouldRegisterAll22EventTypes() {
        // Render domain (5)
        assertEquals(RenderJobCreatedEvent.class, router.resolve("render.job.created"));
        assertEquals(RenderJobStatusChangedEvent.class, router.resolve("render.job.status.changed"));
        assertEquals(RenderJobCompletedEvent.class, router.resolve("render.job.completed"));
        assertEquals(RenderJobFailedEvent.class, router.resolve("render.job.failed"));
        assertEquals(ArtifactCreatedEvent.class, router.resolve("artifact.created"));

        // Timeline domain (3)
        assertEquals(TimelineRevisionCreatedEvent.class, router.resolve("timeline.revision.created"));
        assertEquals(TimelineMergedEvent.class, router.resolve("timeline.merged"));
        assertEquals(TimelineRestoredEvent.class, router.resolve("timeline.restored"));

        // Review domain (6)
        assertEquals(ReviewCreatedEvent.class, router.resolve("review.created"));
        assertEquals(ReviewApprovedEvent.class, router.resolve("review.approved"));
        assertEquals(ReviewRejectedEvent.class, router.resolve("review.rejected"));
        assertEquals(ReviewChangesRequestedEvent.class, router.resolve("review.changes_requested"));
        assertEquals(ReviewCommentAddedEvent.class, router.resolve("review.comment.added"));
        assertEquals(ReviewThreadResolvedEvent.class, router.resolve("review.thread.resolved"));

        // Asset domain (7)
        assertEquals(AssetRegisteredEvent.class, router.resolve("asset.registered"));
        assertEquals(AssetMetadataUpdatedEvent.class, router.resolve("asset.metadata.updated"));
        assertEquals(AssetEnrichedEvent.class, router.resolve("asset.enriched"));
        assertEquals(AssetSubmittedForReviewEvent.class, router.resolve("asset.submitted.review"));
        assertEquals(AssetApprovedEvent.class, router.resolve("asset.approved"));
        assertEquals(AssetPublishedEvent.class, router.resolve("asset.published"));
        assertEquals(AssetArchivedEvent.class, router.resolve("asset.archived"));

        assertEquals(21, router.size()); // 5+3+6+7 = 21 (notification.event.published is handled specially, not routed)
    }

    @Test
    void shouldReturnNullForUnknownEventType() {
        assertNull(router.resolve("unknown.event.type"));
    }
}
