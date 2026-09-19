package com.example.platform.marketplace.api.event;
public record MarketplaceReviewCreatedEvent(MarketplaceEventReference reference, String reviewId) {
    public MarketplaceReviewCreatedEvent {java.util.Objects.requireNonNull(reference);MarketplaceEventReference.require(reviewId);}
    public String factKey(){return reference.factKey();}
}
