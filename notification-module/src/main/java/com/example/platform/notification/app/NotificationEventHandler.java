package com.example.platform.notification.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.notification.domain.*;
import com.example.platform.notification.infrastructure.MockNotificationProvider;
import com.example.platform.shared.events.ArtifactCreatedEvent;
import com.example.platform.shared.events.RenderCacheHashInvalidatedEvent;
import com.example.platform.shared.events.RenderDeliveryCompletedEvent;
import com.example.platform.shared.events.RenderDeliveryFailedEvent;
import com.example.platform.shared.events.RenderJobCreatedEvent;
import com.example.platform.shared.events.RenderJobStatusChangedEvent;
import com.example.platform.shared.events.TimelineMergedEvent;
import com.example.platform.shared.events.TimelineRestoredEvent;
import com.example.platform.shared.events.ReviewApprovedEvent;
import com.example.platform.shared.events.ReviewRejectedEvent;
import com.example.platform.shared.events.ReviewChangesRequestedEvent;
import com.example.platform.shared.events.ReviewCommentAddedEvent;
import com.example.platform.shared.events.ReviewThreadResolvedEvent;
import com.example.platform.shared.events.AssetApprovedEvent;
import com.example.platform.shared.events.AssetPublishedEvent;
import com.example.platform.shared.events.AssetArchivedEvent;
import com.example.platform.shared.events.AssetEnrichedEvent;
import com.example.platform.shared.Ids;
import com.example.platform.shared.Jsons;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventHandler {
    private static final Logger log = LoggerFactory.getLogger(NotificationEventHandler.class);

    private final DSLContext dsl;
    private final List<NotificationProvider> providers;
    private final NotificationRenderingService renderingService;
    private MockNotificationProvider mockProvider;

    public NotificationEventHandler(DSLContext dsl, List<NotificationProvider> providers,
            NotificationRenderingService renderingService) {
        this.dsl = dsl;
        this.providers = providers;
        this.renderingService = renderingService;
    }

    @Autowired(required = false)
    public void setMockNotificationProvider(MockNotificationProvider mockProvider) {
        this.mockProvider = mockProvider;
    }

    @EventListener
    public void onRenderJobCreated(RenderJobCreatedEvent event) {
        log.info("NotificationEventHandler: RenderJobCreatedEvent for job={}", event.renderJobId());
        handle(new NotificationInboundEvent(
                "render.job.created",
                event.renderJobId(),
                Map.of("renderJobId", event.renderJobId(), "projectId", event.projectId(),
                        "profile", event.profile(), "backend", event.primaryBackend())
        ));
    }

    @EventListener
    public void onRenderJobStatusChanged(RenderJobStatusChangedEvent event) {
        log.info("NotificationEventHandler: RenderJobStatusChangedEvent for job={}, {} -> {}",
                event.renderJobId(), event.oldStatus(), event.newStatus());
        String eventType = switch (event.newStatus()) {
            case "COMPLETED" -> "render.job.completed";
            case "FAILED" -> "render.job.failed";
            case "AI_PROCESSING" -> "render.job.ai_processing";
            case "RENDERING" -> "render.job.rendering";
            default -> "render.job.status.changed";
        };
        handle(new NotificationInboundEvent(
                eventType,
                event.renderJobId(),
                Map.of("renderJobId", event.renderJobId(), "projectId", event.projectId(),
                        "oldStatus", event.oldStatus(), "newStatus", event.newStatus())
        ));
    }

    @EventListener
    public void onRenderCacheHashInvalidated(RenderCacheHashInvalidatedEvent event) {
        log.info("NotificationEventHandler: cache hash invalidated job={} tasks={}",
                event.renderJobId(), event.invalidatedCount());
        handle(new NotificationInboundEvent(
                "render.cache.hash_invalidated",
                event.renderJobId(),
                Map.of(
                        "renderJobId", event.renderJobId(),
                        "projectId", event.projectId() != null ? event.projectId() : "",
                        "tenantId", event.tenantId() != null ? event.tenantId() : "",
                        "baseJobId", event.baseJobId() != null ? event.baseJobId() : "",
                        "invalidatedTaskIds", event.invalidatedTaskIds(),
                        "invalidatedCount", event.invalidatedCount(),
                        "detectedAt", event.detectedAt().toString())
        ));
    }

    @EventListener
    public void onRenderDeliveryCompleted(RenderDeliveryCompletedEvent event) {
        handle(new NotificationInboundEvent(
                "render.delivery.completed",
                event.deliveryJobId(),
                Map.of(
                        "deliveryJobId", event.deliveryJobId(),
                        "renderJobId", event.renderJobId(),
                        "projectId", event.projectId() != null ? event.projectId() : "",
                        "tenantId", event.tenantId() != null ? event.tenantId() : "",
                        "destinationId", event.destinationId() != null ? event.destinationId() : "",
                        "protocol", event.protocol() != null ? event.protocol() : "",
                        "remoteUri", event.remoteUri() != null ? event.remoteUri() : "")
        ));
    }

    @EventListener
    public void onRenderDeliveryFailed(RenderDeliveryFailedEvent event) {
        handle(new NotificationInboundEvent(
                "render.delivery.failed",
                event.deliveryJobId(),
                Map.of(
                        "deliveryJobId", event.deliveryJobId(),
                        "renderJobId", event.renderJobId(),
                        "projectId", event.projectId() != null ? event.projectId() : "",
                        "tenantId", event.tenantId() != null ? event.tenantId() : "",
                        "errorMessage", event.errorMessage() != null ? event.errorMessage() : "")
        ));
    }

    @EventListener
    public void onArtifactCreated(ArtifactCreatedEvent event) {
        log.info("NotificationEventHandler: ArtifactCreatedEvent for artifact={}", event.artifactId());
        handle(new NotificationInboundEvent(
                "artifact.created",
                event.artifactId(),
                Map.of("artifactId", event.artifactId(), "renderJobId", event.renderJobId(),
                        "projectId", event.projectId(), "storageUri", event.storageUri())
        ));
    }

    @EventListener
    public void handle(NotificationInboundEvent event) {
        var eventId = Ids.newId("nev");
        dsl.insertInto(table("notification_event"))
                .columns(field("id"), field("event_type"), field("subject_id"), field("payload"), field("created_at"))
                .values(eventId, event.eventType(), event.subjectId(), Jsons.toJson(event.payload()), OffsetDateTime.now())
                .execute();

        var templateCode = NotificationTemplateCode.fromEventType(event.eventType());
        var rendered = renderingService.render(templateCode, event.eventType(), event.subjectId(), event.payload());

        for (var provider : providers) {
            var result = provider.send(new DeliveryCommand(eventId, provider.channel(), rendered.subject(), rendered.body(), Map.of("subjectId", event.subjectId())));
            dsl.insertInto(table("notification_delivery"))
                    .columns(field("id"), field("event_id"), field("channel"), field("provider_code"), field("status"), field("request_payload"), field("response_payload"), field("attempt_count"), field("created_at"))
                    .values(Ids.newId("ndl"), eventId, provider.channel(), provider.providerCode(), result.status(), rendered.body(), result.responsePayload(), 1, OffsetDateTime.now())
                    .execute();
        }
    }

    @EventListener
    public void onTimelineMerged(TimelineMergedEvent event) {
        log.info("NotificationEventHandler: TimelineMerged for merge={}", event.mergeRevisionId());
        handle(new NotificationInboundEvent("timeline.merged", event.mergeRevisionId(),
                Map.of("projectId", event.projectId(), "mergeRevisionId", event.mergeRevisionId())));
    }

    @EventListener
    public void onTimelineRestored(TimelineRestoredEvent event) {
        log.info("NotificationEventHandler: TimelineRestored for new={}", event.newRevisionId());
        handle(new NotificationInboundEvent("timeline.restored", event.newRevisionId(),
                Map.of("projectId", event.projectId(), "restoredFrom", event.restoredFromRevisionId())));
    }

    @EventListener
    public void onReviewApproved(ReviewApprovedEvent event) {
        log.info("NotificationEventHandler: ReviewApproved for review={}", event.reviewId());
        handle(new NotificationInboundEvent("review.approved", event.reviewId(),
                Map.of("reviewId", event.reviewId(), "targetType", event.targetType(),
                        "targetId", event.targetId())));
    }

    @EventListener
    public void onReviewRejected(ReviewRejectedEvent event) {
        log.info("NotificationEventHandler: ReviewRejected for review={}", event.reviewId());
        handle(new NotificationInboundEvent("review.rejected", event.reviewId(),
                Map.of("reviewId", event.reviewId(), "targetType", event.targetType(),
                        "targetId", event.targetId())));
    }

    @EventListener
    public void onReviewChangesRequested(ReviewChangesRequestedEvent event) {
        log.info("NotificationEventHandler: ReviewChangesRequested for review={}", event.reviewId());
        handle(new NotificationInboundEvent("review.changes_requested", event.reviewId(),
                Map.of("reviewId", event.reviewId(), "reviewerUserId", event.reviewerUserId())));
    }

    @EventListener
    public void onReviewCommentAdded(ReviewCommentAddedEvent event) {
        log.info("NotificationEventHandler: ReviewCommentAdded for comment={}", event.commentId());
        handle(new NotificationInboundEvent("review.comment.added", event.commentId(),
                Map.of("reviewId", event.reviewId(), "authorUserId", event.authorUserId())));
    }

    @EventListener
    public void onReviewThreadResolved(ReviewThreadResolvedEvent event) {
        log.info("NotificationEventHandler: ReviewThreadResolved for thread={}", event.threadId());
        handle(new NotificationInboundEvent("review.thread.resolved", event.threadId(),
                Map.of("reviewId", event.reviewId(), "threadId", event.threadId())));
    }

    @EventListener
    public void onAssetApproved(AssetApprovedEvent event) {
        log.info("NotificationEventHandler: AssetApproved for asset={}", event.assetId());
        handle(new NotificationInboundEvent("asset.approved", event.assetId(),
                Map.of("assetId", event.assetId(), "projectId", event.projectId())));
    }

    @EventListener
    public void onAssetPublished(AssetPublishedEvent event) {
        log.info("NotificationEventHandler: AssetPublished for asset={}", event.assetId());
        handle(new NotificationInboundEvent("asset.published", event.assetId(),
                Map.of("assetId", event.assetId(), "projectId", event.projectId(),
                        "assetType", event.assetType())));
    }

    @EventListener
    public void onAssetArchived(AssetArchivedEvent event) {
        log.info("NotificationEventHandler: AssetArchived for asset={}", event.assetId());
        handle(new NotificationInboundEvent("asset.archived", event.assetId(),
                Map.of("assetId", event.assetId(), "projectId", event.projectId())));
    }

    @EventListener
    public void onAssetEnriched(AssetEnrichedEvent event) {
        log.info("NotificationEventHandler: AssetEnriched for asset={}", event.assetId());
        handle(new NotificationInboundEvent("asset.enriched", event.assetId(),
                Map.of("assetId", event.assetId(), "status", event.enrichmentStatus())));
    }
}
