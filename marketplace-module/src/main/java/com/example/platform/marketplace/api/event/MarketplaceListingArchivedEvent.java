package com.example.platform.marketplace.api.event;
public record MarketplaceListingArchivedEvent(MarketplaceEventReference reference) {
    public MarketplaceListingArchivedEvent {java.util.Objects.requireNonNull(reference);}
    public String factKey(){return reference.factKey();}
}
