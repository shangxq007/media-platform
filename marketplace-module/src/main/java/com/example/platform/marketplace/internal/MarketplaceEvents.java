package com.example.platform.marketplace.internal;
import static com.example.platform.marketplace.internal.MarketplaceOutboxEvents.*;
import com.example.platform.marketplace.api.MarketplaceApi.*;
import com.example.platform.marketplace.api.event.*;
import com.example.platform.identity.api.project.ProjectScope;
import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.shared.authorization.CanonicalActor;
import java.time.Instant;
import java.util.UUID;
/** Only called inside the owner's command transaction; common Outbox remains the transport. */
@org.springframework.stereotype.Component
public class MarketplaceEvents {
    private final OutboxEventService outbox;
    public MarketplaceEvents(OutboxEventService outbox){this.outbox=outbox;}
    private MarketplaceEventReference reference(Listing row,CanonicalActor actor) {
        if(!row.tenantId().equals(actor.tenantId()))throw new IllegalArgumentException("Fact actor scope mismatch");
        return new MarketplaceEventReference("mfact_"+UUID.randomUUID(),row.id(),row.subject(),
                new ProjectScope(row.tenantId(),row.workspaceId(),row.projectId()),row.version(),
                actor.actorId(),actor.accountId(),actor.actorType(),Instant.now());
    }
    private String key(Listing row){return "marketplace:"+row.id()+":"+row.version();}
    void listingCreated(Listing r,CanonicalActor a){outbox.append(LISTING_CREATED.append(r.tenantId(),new MarketplaceListingCreatedEvent(reference(r,a)),key(r)));}
    void listingUpdated(Listing r,CanonicalActor a){outbox.append(LISTING_UPDATED.append(r.tenantId(),new MarketplaceListingUpdatedEvent(reference(r,a)),key(r)));}
    void reviewCreated(Listing r,String review,CanonicalActor a){outbox.append(REVIEW_CREATED.append(r.tenantId(),new MarketplaceReviewCreatedEvent(reference(r,a),review),key(r)));}
    void decided(Listing r,String review,String decisionId,String decision,CanonicalActor a){
        var ref=reference(r,a);
        switch(decision){
            case "APPROVE" -> outbox.append(REVIEW_APPROVED.append(r.tenantId(),new MarketplaceReviewApprovedEvent(ref,review,decisionId),key(r)));
            case "REQUEST_CHANGES" -> outbox.append(REVIEW_CHANGES_REQUESTED.append(r.tenantId(),new MarketplaceReviewChangesRequestedEvent(ref,review,decisionId),key(r)));
            case "REJECT" -> outbox.append(REVIEW_REJECTED.append(r.tenantId(),new MarketplaceReviewRejectedEvent(ref,review,decisionId),key(r)));
            default -> throw new IllegalArgumentException("Unknown review decision");
        }
    }
    void commentAdded(Listing r,String review,String thread,String comment,CanonicalActor a){outbox.append(REVIEW_COMMENT_ADDED.append(r.tenantId(),new MarketplaceReviewCommentAddedEvent(reference(r,a),review,thread,comment),key(r)));}
    void threadResolved(Listing r,String review,String thread,CanonicalActor a){outbox.append(REVIEW_THREAD_RESOLVED.append(r.tenantId(),new MarketplaceReviewThreadResolvedEvent(reference(r,a),review,thread),key(r)));}
    void published(Listing r,CanonicalActor a){outbox.append(LISTING_PUBLISHED.append(r.tenantId(),new MarketplaceListingPublishedEvent(reference(r,a),r.reviewId()),key(r)));}
    void archived(Listing r,CanonicalActor a){outbox.append(LISTING_ARCHIVED.append(r.tenantId(),new MarketplaceListingArchivedEvent(reference(r,a)),key(r)));}
}
