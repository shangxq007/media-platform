package com.example.platform.marketplace.api.event;
public record MarketplaceListingUpdatedEvent(MarketplaceEventReference reference) {
    public MarketplaceListingUpdatedEvent {java.util.Objects.requireNonNull(reference);}
    public String factKey(){return reference.factKey();}
}
