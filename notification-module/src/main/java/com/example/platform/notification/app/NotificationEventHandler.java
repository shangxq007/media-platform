package com.example.platform.notification.app;

import static com.example.platform.typedschema.jooq.generated.tables.NotificationDelivery.NOTIFICATION_DELIVERY;
import static com.example.platform.typedschema.jooq.generated.tables.NotificationEvent.NOTIFICATION_EVENT;

import com.example.platform.notification.domain.*;
import com.example.platform.notification.api.ingress.NotificationInboundEvent;
import com.example.platform.notification.infrastructure.MockNotificationProvider;
import com.example.platform.artifact.api.event.ArtifactCreatedEvent;
import com.example.platform.render.api.event.RenderCacheHashInvalidatedEvent;
import com.example.platform.delivery.api.event.DeliveryCompletedEvent;
import com.example.platform.delivery.api.event.DeliveryFailedEvent;
import com.example.platform.render.api.event.RenderJobCreatedEvent;
import com.example.platform.render.api.event.RenderJobStatusChangedEvent;
import com.example.platform.timeline.api.event.TimelineMergedEvent;
import com.example.platform.timeline.api.event.TimelineRestoredEvent;
import com.example.platform.timeline.api.event.TimelineReviewApprovedEvent;
import com.example.platform.timeline.api.event.TimelineReviewRejectedEvent;
import com.example.platform.timeline.api.event.TimelineReviewChangesRequestedEvent;
import com.example.platform.timeline.api.event.TimelineReviewCommentAddedEvent;
import com.example.platform.timeline.api.event.TimelineReviewThreadResolvedEvent;
import com.example.platform.shared.events.AssetApprovedEvent;
import com.example.platform.shared.events.AssetPublishedEvent;
import com.example.platform.shared.events.AssetArchivedEvent;
import com.example.platform.artifact.api.event.AssetEnrichedEvent;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@org.springframework.transaction.annotation.Transactional
public class NotificationEventHandler {
    private static final Logger log = LoggerFactory.getLogger(NotificationEventHandler.class);

    private final DSLContext dsl;
    private final List<NotificationProvider> providers;
    private final NotificationRenderingService renderingService;
    private final MockNotificationProvider mockProvider;

    public NotificationEventHandler(DSLContext dsl, List<NotificationProvider> providers,
            NotificationRenderingService renderingService,
            @Autowired(required = false) MockNotificationProvider mockProvider) {
        this.dsl = dsl;
        this.providers = providers;
        this.renderingService = renderingService;
        this.mockProvider = mockProvider;
    }

    @EventListener
    public void onRenderJobCreated(RenderJobCreatedEvent event) {
        log.info("NotificationEventHandler: RenderJobCreatedEvent for job={}", event.renderJobId());
        handleFact(event.factKey(), new NotificationInboundEvent(
                "render.job.created",
                event.renderJobId(),
                Map.of("renderJobId", event.renderJobId(), "projectId", event.projectId(),
                        "profile", event.profile(), "tenantId", event.tenantId())
        ));
    }

    @EventListener
    public void onRenderJobStatusChanged(RenderJobStatusChangedEvent event) {
        log.info("NotificationEventHandler: RenderJobStatusChangedEvent for job={}, {} -> {}",
                event.renderJobId(), event.oldStatus(), event.newStatus());
        String eventType = switch (event.newStatus()) {
            case COMPLETED -> "render.job.completed";
            case FAILED -> "render.job.failed";
            case SELECTING_PROVIDER -> "render.job.ai_processing";
            case EXECUTING -> "render.job.rendering";
            default -> "render.job.status.changed";
        };
        handleFact(event.factKey(), new NotificationInboundEvent(
                eventType,
                event.renderJobId(),
                Map.of("renderJobId", event.renderJobId(), "projectId", event.projectId(),
                        "oldStatus", event.oldStatus().name(), "newStatus", event.newStatus().name(), "tenantId", event.tenantId())
        ));
    }

    @EventListener
    public void onRenderCacheHashInvalidated(RenderCacheHashInvalidatedEvent event) {
        log.info("NotificationEventHandler: cache hash invalidated job={} tasks={}",
                event.renderJobId(), event.invalidatedCount());
        handleFact(event.factKey(), new NotificationInboundEvent(
                "render.cache.hash_invalidated",
                event.renderJobId(),
                Map.of(
                        "renderJobId", event.renderJobId(),
                        "projectId", event.projectId(),
                        "tenantId", event.tenantId(),
                        "baseJobId", event.baseJobId(),
                        "invalidatedTaskIds", event.invalidatedTaskIds(),
                        "invalidatedCount", event.invalidatedCount(),
                        "detectedAt", event.detectedAt().toString())
        ));
    }

    @EventListener
    public void onRenderDeliveryCompleted(DeliveryCompletedEvent event) {
        handleFact(event.factKey(), new NotificationInboundEvent(
                "render.delivery.completed",
                event.deliveryJobId(),
                Map.of(
                        "deliveryJobId", event.deliveryJobId(),
                        "renderJobId", event.renderJobId(),
                        "projectId", event.projectId() != null ? event.projectId() : "",
                        "tenantId", event.tenantId() != null ? event.tenantId() : "",
                        "destinationId", event.destinationId() != null ? event.destinationId() : "",
                        "protocol", event.protocol().name(),
                        "remoteUri", event.remoteUri() != null ? event.remoteUri() : "")
        ));
    }

    @EventListener
    public void onRenderDeliveryFailed(DeliveryFailedEvent event) {
        handleFact(event.factKey(), new NotificationInboundEvent(
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
        handleFact(event.factKey(), new NotificationInboundEvent(
                "artifact.created",
                event.artifactId(),
                Map.of("artifactId", event.artifactId(), "renderJobId", event.renderJobId(),
                        "projectId", event.projectId(), "tenantId", event.tenantId())
        ));
    }

    @EventListener
    public void handle(NotificationInboundEvent event) {
        deliver("nev_"+java.util.UUID.randomUUID().toString().replace("-",""),event);
    }

    private void handleFact(String factKey,NotificationInboundEvent event) {
        deliver("nev_"+java.util.UUID.nameUUIDFromBytes(factKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)),event);
    }

    private void deliver(String eventId,NotificationInboundEvent event) {
        int inserted=dsl.insertInto(NOTIFICATION_EVENT)
                .columns(NOTIFICATION_EVENT.ID, NOTIFICATION_EVENT.EVENT_TYPE, NOTIFICATION_EVENT.SUBJECT_ID, NOTIFICATION_EVENT.PAYLOAD, NOTIFICATION_EVENT.CREATED_AT)
                .values(eventId, event.eventType(), event.subjectId(), NotificationPayloadJson.toJson(event.payload()), LocalDateTime.now(ZoneOffset.UTC))
                .onConflict(NOTIFICATION_EVENT.ID).doNothing().execute();
        if(inserted==0)return;

        var templateCode = NotificationTemplateCode.fromEventType(event.eventType());
        var rendered = renderingService.render(templateCode, event.eventType(), event.subjectId(), event.payload());

        for (var provider : providers) {
            var result = provider.send(new DeliveryCommand(eventId, provider.channel(), rendered.subject(), rendered.body(), Map.of("subjectId", event.subjectId())));
            dsl.insertInto(NOTIFICATION_DELIVERY)
                    .columns(NOTIFICATION_DELIVERY.ID, NOTIFICATION_DELIVERY.EVENT_ID, NOTIFICATION_DELIVERY.CHANNEL, NOTIFICATION_DELIVERY.PROVIDER_CODE, NOTIFICATION_DELIVERY.STATUS, NOTIFICATION_DELIVERY.REQUEST_PAYLOAD, NOTIFICATION_DELIVERY.RESPONSE_PAYLOAD, NOTIFICATION_DELIVERY.ATTEMPT_COUNT, NOTIFICATION_DELIVERY.CREATED_AT)
                    .values(("ndl_" + java.util.UUID.randomUUID().toString().replace("-", "")), eventId, provider.channel(), provider.providerCode(), result.status(), rendered.body(), result.responsePayload(), 1, LocalDateTime.now(ZoneOffset.UTC))
                    .execute();
        }
    }

    @EventListener
    public void onTimelineMerged(TimelineMergedEvent event) {
        log.info("NotificationEventHandler: TimelineMerged for merge={}", event.mergeRevisionId());
        handleFact(event.factKey(), new NotificationInboundEvent("timeline.merged", event.mergeRevisionId(),
                Map.of("tenantId",event.tenantId(),"projectId",event.projectId(), "mergeRevisionId", event.mergeRevisionId())));
    }

    @EventListener
    public void onTimelineRestored(TimelineRestoredEvent event) {
        log.info("NotificationEventHandler: TimelineRestored for new={}", event.newRevisionId());
        handleFact(event.factKey(), new NotificationInboundEvent("timeline.restored", event.newRevisionId(),
                Map.of("tenantId",event.tenantId(),"projectId",event.projectId(), "restoredFrom", event.restoredFromRevisionId())));
    }

    @EventListener
    public void onReviewApproved(TimelineReviewApprovedEvent event) {
        log.info("NotificationEventHandler: ReviewApproved for review={}", event.reviewId());
        handleFact(event.factKey(), new NotificationInboundEvent("review.approved", event.reviewId(),
                Map.of("tenantId",event.tenantId(),"projectId",event.projectId(),"reviewId", event.reviewId(), "targetType", "TIMELINE",
                        "targetId", event.revisionId())));
    }

    @EventListener
    public void onReviewRejected(TimelineReviewRejectedEvent event) {
        log.info("NotificationEventHandler: ReviewRejected for review={}", event.reviewId());
        handleFact(event.factKey(), new NotificationInboundEvent("review.rejected", event.reviewId(),
                Map.of("tenantId",event.tenantId(),"projectId",event.projectId(),"reviewId", event.reviewId(), "targetType", "TIMELINE",
                        "targetId", event.revisionId())));
    }

    @EventListener
    public void onReviewChangesRequested(TimelineReviewChangesRequestedEvent event) {
        log.info("NotificationEventHandler: ReviewChangesRequested for review={}", event.reviewId());
        handleFact(event.factKey(), new NotificationInboundEvent("review.changes_requested", event.reviewId(),
                Map.of("tenantId",event.tenantId(),"projectId",event.projectId(),"reviewId", event.reviewId(), "reviewerUserId", event.reviewerUserId())));
    }

    @EventListener
    public void onReviewCommentAdded(TimelineReviewCommentAddedEvent event) {
        log.info("NotificationEventHandler: ReviewCommentAdded for comment={}", event.commentId());
        handleFact(event.factKey(), new NotificationInboundEvent("review.comment.added", event.commentId(),
                Map.of("tenantId",event.tenantId(),"projectId",event.projectId(),"reviewId", event.reviewId(), "authorUserId", event.authorUserId())));
    }

    @EventListener
    public void onReviewThreadResolved(TimelineReviewThreadResolvedEvent event) {
        log.info("NotificationEventHandler: ReviewThreadResolved for thread={}", event.threadId());
        handleFact(event.factKey(), new NotificationInboundEvent("review.thread.resolved", event.threadId(),
                Map.of("tenantId",event.tenantId(),"projectId",event.projectId(),"reviewId", event.reviewId(), "threadId", event.threadId())));
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
        handleFact(event.factKey(), new NotificationInboundEvent("asset.enriched", event.assetId(),
                Map.of("assetId", event.assetId(), "status", event.enrichmentStatus(), "tenantId", event.tenantId(), "projectId", event.projectId())));
    }
}
