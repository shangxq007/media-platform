package com.example.platform.marketplace.internal;
import com.example.platform.outbox.api.event.*;
import com.example.platform.marketplace.api.event.*;
import java.util.List;
@org.springframework.stereotype.Component
public class MarketplaceOutboxEvents implements OutboxEventCatalog {
    static final OutboxEventType<MarketplaceListingCreatedEvent> LISTING_CREATED=new OutboxEventType<>("marketplace.listing.created",1,"marketplace_listing",MarketplaceListingCreatedEvent.class,e->e.reference().listingId(),e->e.reference().scope().tenantId());
    static final OutboxEventType<MarketplaceListingUpdatedEvent> LISTING_UPDATED=new OutboxEventType<>("marketplace.listing.updated",1,"marketplace_listing",MarketplaceListingUpdatedEvent.class,e->e.reference().listingId(),e->e.reference().scope().tenantId());
    static final OutboxEventType<MarketplaceReviewCreatedEvent> REVIEW_CREATED=new OutboxEventType<>("marketplace.review.created",1,"marketplace_review",MarketplaceReviewCreatedEvent.class,e->e.reviewId(),e->e.reference().scope().tenantId());
    static final OutboxEventType<MarketplaceReviewApprovedEvent> REVIEW_APPROVED=new OutboxEventType<>("marketplace.review.approved",1,"marketplace_review",MarketplaceReviewApprovedEvent.class,e->e.reviewId(),e->e.reference().scope().tenantId());
    static final OutboxEventType<MarketplaceReviewRejectedEvent> REVIEW_REJECTED=new OutboxEventType<>("marketplace.review.rejected",1,"marketplace_review",MarketplaceReviewRejectedEvent.class,e->e.reviewId(),e->e.reference().scope().tenantId());
    static final OutboxEventType<MarketplaceReviewChangesRequestedEvent> REVIEW_CHANGES_REQUESTED=new OutboxEventType<>("marketplace.review.changes_requested",1,"marketplace_review",MarketplaceReviewChangesRequestedEvent.class,e->e.reviewId(),e->e.reference().scope().tenantId());
    static final OutboxEventType<MarketplaceReviewCommentAddedEvent> REVIEW_COMMENT_ADDED=new OutboxEventType<>("marketplace.review.comment.added",1,"marketplace_review",MarketplaceReviewCommentAddedEvent.class,e->e.reviewId(),e->e.reference().scope().tenantId());
    static final OutboxEventType<MarketplaceReviewThreadResolvedEvent> REVIEW_THREAD_RESOLVED=new OutboxEventType<>("marketplace.review.thread.resolved",1,"marketplace_review",MarketplaceReviewThreadResolvedEvent.class,e->e.reviewId(),e->e.reference().scope().tenantId());
    static final OutboxEventType<MarketplaceListingPublishedEvent> LISTING_PUBLISHED=new OutboxEventType<>("marketplace.listing.published",1,"marketplace_listing",MarketplaceListingPublishedEvent.class,e->e.reference().listingId(),e->e.reference().scope().tenantId());
    static final OutboxEventType<MarketplaceListingArchivedEvent> LISTING_ARCHIVED=new OutboxEventType<>("marketplace.listing.archived",1,"marketplace_listing",MarketplaceListingArchivedEvent.class,e->e.reference().listingId(),e->e.reference().scope().tenantId());
    public List<OutboxEventType<?>> types(){return List.of(LISTING_CREATED,LISTING_UPDATED,REVIEW_CREATED,REVIEW_APPROVED,REVIEW_REJECTED,REVIEW_CHANGES_REQUESTED,REVIEW_COMMENT_ADDED,REVIEW_THREAD_RESOLVED,LISTING_PUBLISHED,LISTING_ARCHIVED);}
}
