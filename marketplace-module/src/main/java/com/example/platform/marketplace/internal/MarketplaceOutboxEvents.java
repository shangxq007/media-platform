package com.example.platform.marketplace.internal;
import com.example.platform.outbox.api.event.*;
import com.example.platform.shared.events.*;
import java.util.List;
@org.springframework.stereotype.Component
public class MarketplaceOutboxEvents implements OutboxEventCatalog {
    static final OutboxEventType<AssetSubmittedForReviewEvent> SUBMITTED=new OutboxEventType<>("asset.submitted.review",1,"ASSET",AssetSubmittedForReviewEvent.class,AssetSubmittedForReviewEvent::assetId,e->null);
    static final OutboxEventType<AssetApprovedEvent> APPROVED=new OutboxEventType<>("asset.approved",1,"ASSET",AssetApprovedEvent.class,AssetApprovedEvent::assetId,e->null);
    static final OutboxEventType<AssetPublishedEvent> PUBLISHED=new OutboxEventType<>("asset.published",1,"ASSET",AssetPublishedEvent.class,AssetPublishedEvent::assetId,e->null);
    static final OutboxEventType<AssetArchivedEvent> ARCHIVED=new OutboxEventType<>("asset.archived",1,"ASSET",AssetArchivedEvent.class,AssetArchivedEvent::assetId,e->null);
    public List<OutboxEventType<?>> types(){return List.of(SUBMITTED,APPROVED,PUBLISHED,ARCHIVED);}
}
