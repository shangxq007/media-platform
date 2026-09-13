package com.example.platform.render.app.event;

import com.example.platform.outbox.api.event.*;
import com.example.platform.shared.events.*;
import com.example.platform.render.api.event.*;
import java.util.List;
import org.springframework.stereotype.Component;

/** Versioned definitions for the existing typed facts published by this owner/consumer. */
@Component
@org.springframework.modulith.NamedInterface("events")
public final class RenderOutboxEvents implements OutboxEventCatalog {
    public static final OutboxEventType<AssetSubmittedForReviewEvent> ASSETSUBMITTEDFORREVIEWEVENT = new OutboxEventType<>("asset.submitted.review", 1, "ASSET", AssetSubmittedForReviewEvent.class, AssetSubmittedForReviewEvent::assetId, event -> null);
    public static final OutboxEventType<AssetApprovedEvent> ASSETAPPROVEDEVENT = new OutboxEventType<>("asset.approved", 1, "ASSET", AssetApprovedEvent.class, AssetApprovedEvent::assetId, event -> null);
    public static final OutboxEventType<AssetPublishedEvent> ASSETPUBLISHEDEVENT = new OutboxEventType<>("asset.published", 1, "ASSET", AssetPublishedEvent.class, AssetPublishedEvent::assetId, event -> null);
    public static final OutboxEventType<AssetArchivedEvent> ASSETARCHIVEDEVENT = new OutboxEventType<>("asset.archived", 1, "ASSET", AssetArchivedEvent.class, AssetArchivedEvent::assetId, event -> null);
    public static final OutboxEventType<RenderJobCreatedEvent> RENDERJOBCREATEDEVENT = new OutboxEventType<>("render.job.created", 2, "render_job", RenderJobCreatedEvent.class, RenderJobCreatedEvent::renderJobId, RenderJobCreatedEvent::tenantId);
    public static final OutboxEventType<RenderJobStatusChangedEvent> RENDERJOBSTATUSCHANGEDEVENT = new OutboxEventType<>("render.job.status.changed", 2, "render_job", RenderJobStatusChangedEvent.class, RenderJobStatusChangedEvent::renderJobId, RenderJobStatusChangedEvent::tenantId);
    public static final OutboxEventType<RenderJobCompletedEvent> RENDERJOBCOMPLETEDEVENT = new OutboxEventType<>("render.job.completed", 2, "render_job", RenderJobCompletedEvent.class, RenderJobCompletedEvent::renderJobId, event -> event.initiator().tenantId());
    public static final OutboxEventType<RenderJobFailedEvent> RENDERJOBFAILEDEVENT = new OutboxEventType<>("render.job.failed", 2, "render_job", RenderJobFailedEvent.class, RenderJobFailedEvent::renderJobId, event -> event.initiator().tenantId());
    public static final OutboxEventType<RenderCacheHashInvalidatedEvent> CACHE_INVALIDATED = new OutboxEventType<>("render.cache.hash.invalidated", 1, "render_job", RenderCacheHashInvalidatedEvent.class, RenderCacheHashInvalidatedEvent::renderJobId, RenderCacheHashInvalidatedEvent::tenantId);
    @Override public List<OutboxEventType<?>> types() { return List.of(ASSETSUBMITTEDFORREVIEWEVENT, ASSETAPPROVEDEVENT, ASSETPUBLISHEDEVENT, ASSETARCHIVEDEVENT, RENDERJOBCREATEDEVENT, RENDERJOBSTATUSCHANGEDEVENT, RENDERJOBCOMPLETEDEVENT, RENDERJOBFAILEDEVENT, CACHE_INVALIDATED); }
}
