package com.example.platform.marketplace.api.event;
public record MarketplaceListingPublishedEvent(MarketplaceEventReference reference, String reviewId) {
    public MarketplaceListingPublishedEvent {java.util.Objects.requireNonNull(reference);MarketplaceEventReference.require(reviewId);}
    public String factKey(){return reference.factKey();}
}
