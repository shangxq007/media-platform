package com.example.platform.marketplace.api.event;
public record MarketplaceReviewApprovedEvent(MarketplaceEventReference reference, String reviewId, String decisionId) {
    public MarketplaceReviewApprovedEvent {java.util.Objects.requireNonNull(reference);MarketplaceEventReference.require(reviewId);MarketplaceEventReference.require(decisionId);}
    public String factKey(){return reference.factKey();}
}
