package com.example.platform.commerce.domain;

import com.example.platform.shared.commercial.Money;
import java.time.Instant;

/** Versioned packaging of references to independent commercial authorities. */
public record CommercialOffering(
        String offeringId, String productId, String productCode, ProductLineType productLineType, String displayName,
        String offeringKey, long offeringVersion,
        OfferingLifecycleState lifecycleState, long rowVersion, PurchaseMode purchaseMode,
        String tenantScope, String marketScope, Instant validFrom, Instant validTo,
        AuthorityReference entitlementBundleReference, AuthorityReference quotaProfileReference,
        AuthorityReference subscriptionPlanReference, AuthorityReference commercialPriceReference,
        Money priceSnapshot, Long creditQuantityMinor, Integer seatQuantity, String seatFeatureKey,
        Instant createdAt, Instant updatedAt) {

    public boolean appliesTo(String tenantId, String market, Instant at) {
        return lifecycleState == OfferingLifecycleState.ACTIVE
                && ("GLOBAL".equals(tenantScope) || tenantScope.equals(tenantId))
                && ("GLOBAL".equals(marketScope) || marketScope.equalsIgnoreCase(market))
                && !at.isBefore(validFrom) && (validTo == null || at.isBefore(validTo));
    }
}
