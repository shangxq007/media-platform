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
        outboxEventService.append(RenderOutboxEvents.TIMELINEREVISIONCREATEDEVENT.append(com.example.platform.shared.web.TenantGuard.requireTenantId(), event, null));
        log.debug("Outbox: TimelineRevisionCreatedEvent rev={}", event.revisionId());
    }

    public void publish(TimelineMergedEvent event) {
        outboxEventService.append(RenderOutboxEvents.TIMELINEMERGEDEVENT.append(com.example.platform.shared.web.TenantGuard.requireTenantId(), event, null));
        log.info("Outbox: TimelineMergedEvent merge={}", event.mergeRevisionId());
    }

    public void publish(TimelineRestoredEvent event) {
        outboxEventService.append(RenderOutboxEvents.TIMELINERESTOREDEVENT.append(com.example.platform.shared.web.TenantGuard.requireTenantId(), event, null));
        log.info("Outbox: TimelineRestoredEvent new={}", event.newRevisionId());
    }

    public void publish(AssetSubmittedForReviewEvent event) {
        outboxEventService.append(RenderOutboxEvents.ASSETSUBMITTEDFORREVIEWEVENT.append(com.example.platform.shared.web.TenantGuard.requireTenantId(), event, null));
        log.info("Outbox: AssetSubmittedForReviewEvent asset={}", event.assetId());
    }

    public void publish(AssetApprovedEvent event) {
        outboxEventService.append(RenderOutboxEvents.ASSETAPPROVEDEVENT.append(com.example.platform.shared.web.TenantGuard.requireTenantId(), event, null));
        log.info("Outbox: AssetApprovedEvent asset={}", event.assetId());
    }

    public void publish(AssetPublishedEvent event) {
        outboxEventService.append(RenderOutboxEvents.ASSETPUBLISHEDEVENT.append(com.example.platform.shared.web.TenantGuard.requireTenantId(), event, null));
        log.info("Outbox: AssetPublishedEvent asset={}", event.assetId());
    }

    public void publish(AssetArchivedEvent event) {
        outboxEventService.append(RenderOutboxEvents.ASSETARCHIVEDEVENT.append(com.example.platform.shared.web.TenantGuard.requireTenantId(), event, null));
        log.info("Outbox: AssetArchivedEvent asset={}", event.assetId());
    }
}
