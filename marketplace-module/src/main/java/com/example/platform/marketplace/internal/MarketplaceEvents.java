package com.example.platform.marketplace.internal;

import com.example.platform.marketplace.api.MarketplaceApi.*;
import com.example.platform.marketplace.api.MarketplacePublicationSubjectRef.MediaAssetSubject;
import com.example.platform.outbox.api.event.*;
import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.shared.events.*;
import com.example.platform.shared.authorization.CanonicalActor;
import java.util.List;
import org.springframework.stereotype.Component;

/** EP15 owns the producer transaction; EP29C replaces these existing payload schemas. */
@Component
public class MarketplaceEvents implements OutboxEventCatalog {
    static final OutboxEventType<AssetSubmittedForReviewEvent> SUBMITTED=new OutboxEventType<>("asset.submitted.review",1,"ASSET",AssetSubmittedForReviewEvent.class,AssetSubmittedForReviewEvent::assetId,e->null);
    static final OutboxEventType<AssetApprovedEvent> APPROVED=new OutboxEventType<>("asset.approved",1,"ASSET",AssetApprovedEvent.class,AssetApprovedEvent::assetId,e->null);
    static final OutboxEventType<AssetPublishedEvent> PUBLISHED=new OutboxEventType<>("asset.published",1,"ASSET",AssetPublishedEvent.class,AssetPublishedEvent::assetId,e->null);
    static final OutboxEventType<AssetArchivedEvent> ARCHIVED=new OutboxEventType<>("asset.archived",1,"ASSET",AssetArchivedEvent.class,AssetArchivedEvent::assetId,e->null);
    private final OutboxEventService outbox;
    public MarketplaceEvents(OutboxEventService outbox){this.outbox=outbox;}
    public List<OutboxEventType<?>> types(){return List.of(SUBMITTED,APPROVED,PUBLISHED,ARCHIVED);}
    void listingCreated(Listing row,CanonicalActor actor) {}
    void listingUpdated(Listing row,CanonicalActor actor) {}
    void reviewCreated(Listing row,String review,CanonicalActor actor) {outbox.append(SUBMITTED.append(row.tenantId(),new AssetSubmittedForReviewEvent(asset(row),row.projectId(),review),key(row)));}
    void decided(Listing row,String review,String decision,CanonicalActor actor) {
        if(decision.equals("APPROVE"))outbox.append(APPROVED.append(row.tenantId(),new AssetApprovedEvent(asset(row),row.projectId(),review),key(row)));
    }
    void commentAdded(Listing row,String review,String thread,String comment,CanonicalActor actor) {}
    void threadResolved(Listing row,String review,String thread,CanonicalActor actor) {}
    void published(Listing row,CanonicalActor actor) {outbox.append(PUBLISHED.append(row.tenantId(),new AssetPublishedEvent(asset(row),((MediaAssetSubject)row.subject()).version(),"MEDIA",row.projectId(),"PUBLISHED"),key(row)));}
    void archived(Listing row,CanonicalActor actor) {outbox.append(ARCHIVED.append(row.tenantId(),new AssetArchivedEvent(asset(row),"MEDIA",row.projectId()),key(row)));}
    private String asset(Listing row){return ((MediaAssetSubject)row.subject()).assetId().value();}
    private String key(Listing row){return "marketplace:"+row.id()+":"+row.version();}
}
