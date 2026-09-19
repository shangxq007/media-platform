package com.example.platform.marketplace.api.event;
public record MarketplaceReviewChangesRequestedEvent(MarketplaceEventReference reference, String reviewId, String decisionId) {
    public MarketplaceReviewChangesRequestedEvent {java.util.Objects.requireNonNull(reference);MarketplaceEventReference.require(reviewId);MarketplaceEventReference.require(decisionId);}
    public String factKey(){return reference.factKey();}
}
