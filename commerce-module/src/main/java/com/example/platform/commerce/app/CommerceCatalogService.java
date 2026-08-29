package com.example.platform.commerce.app;

import com.example.platform.commerce.domain.*;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CommerceCatalogService {
    private final ProductCatalogAuthority authority;
    private final Clock clock;

    @Autowired
    public CommerceCatalogService(ProductCatalogAuthority authority) { this(authority, Clock.systemUTC()); }
    public CommerceCatalogService(ProductCatalogAuthority authority, Clock clock) { this.authority = authority; this.clock = clock; }

    public List<CanonicalProduct> listProducts(CatalogReadScope scope, String market) {
        return authority.listForCheckout(scope, market, clock.instant()).stream().map(CommerceCatalogService::project).toList();
    }
    public Optional<CanonicalProduct> findProduct(CatalogReadScope scope, String market, String code) {
        return authority.resolveForCheckout(scope, market, code, clock.instant()).map(CommerceCatalogService::project);
    }
    public CanonicalProduct requireProduct(CatalogReadScope scope, String market, String code) {
        return findProduct(scope, market, code).orElseThrow(() -> new IllegalArgumentException("active applicable product not found: " + code));
    }
    public CommercialOffering requireOffering(CatalogReadScope scope, String market, String code, Instant at) {
        return authority.resolveForCheckout(scope, market, code, at).orElseThrow(() -> new IllegalArgumentException("active applicable offering not found: " + code));
    }
    public CommercialOffering requireHistorical(CatalogReadScope scope, String offeringId, long version) {
        return authority.findHistorical(scope, offeringId, version).orElseThrow(() -> new IllegalArgumentException("historical offering not found: " + offeringId));
    }
    static CanonicalProduct project(CommercialOffering o) {
        return new CanonicalProduct(o.productCode(), o.purchaseMode(), o.productLineType(),
                o.entitlementBundleReference() == null ? null : o.entitlementBundleReference().key(),
                o.quotaProfileReference() == null ? null : o.quotaProfileReference().key(),
                o.subscriptionPlanReference() == null ? null : o.subscriptionPlanReference().key(),
                null, null, o.creditQuantityMinor(), o.seatQuantity(), o.seatFeatureKey(),
                o.priceSnapshot().amountMinor(), o.priceSnapshot().currency(), o.displayName());
    }
}
