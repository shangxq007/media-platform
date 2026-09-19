package com.example.platform.marketplace.api.event;
public record MarketplaceReviewThreadResolvedEvent(MarketplaceEventReference reference, String reviewId, String threadId) {
    public MarketplaceReviewThreadResolvedEvent {java.util.Objects.requireNonNull(reference);MarketplaceEventReference.require(reviewId);MarketplaceEventReference.require(threadId);}
    public String factKey(){return reference.factKey();}
}
