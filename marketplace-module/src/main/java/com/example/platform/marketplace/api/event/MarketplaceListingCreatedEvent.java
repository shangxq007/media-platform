package com.example.platform.marketplace.api.event;
public record MarketplaceListingCreatedEvent(MarketplaceEventReference reference) {
    public MarketplaceListingCreatedEvent {java.util.Objects.requireNonNull(reference);}
    public String factKey(){return reference.factKey();}
}
