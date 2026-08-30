package com.example.platform.commerce.domain;

import com.example.platform.shared.commercial.Money;
import java.time.Instant;

public record CreateCommercialOfferingCommand(
        CatalogActor actor, String productId, String productCode, ProductLineType lineType, String displayName,
        String offeringId, String offeringKey, long offeringVersion, PurchaseMode purchaseMode,
        String tenantScope, String marketScope, Instant validFrom, Instant validTo,
        AuthorityReference entitlementBundleReference, AuthorityReference quotaProfileReference,
        AuthorityReference subscriptionPlanReference, AuthorityReference commercialPriceReference,
        Money priceSnapshot, Long creditQuantityMinor, Integer seatQuantity, String seatFeatureKey,
        long expectedVersion, String idempotencyKey, String source, String reason, String traceId, Instant occurredAt) {
    public CreateCommercialOfferingCommand {
        if (actor == null || productId == null || productId.isBlank() || productCode == null || productCode.isBlank()
                || lineType == null || displayName == null || displayName.isBlank() || offeringId == null || offeringId.isBlank()
                || offeringKey == null || offeringKey.isBlank() || offeringVersion < 1 || purchaseMode == null
                || tenantScope == null || tenantScope.isBlank() || marketScope == null || marketScope.isBlank()
                || validFrom == null || commercialPriceReference == null || priceSnapshot == null
                || expectedVersion != 0 || idempotencyKey == null || idempotencyKey.isBlank()
                || source == null || source.isBlank() || reason == null || reason.isBlank()
                || traceId == null || traceId.isBlank() || occurredAt == null) {
            throw new IllegalArgumentException("complete catalog create command is required");
        }
        if (validTo != null && !validTo.isAfter(validFrom)) throw new IllegalArgumentException("validTo must follow validFrom");
        if (purchaseMode == PurchaseMode.SUBSCRIPTION && subscriptionPlanReference == null) {
            throw new IllegalArgumentException("subscription offering requires a subscription plan reference");
        }
        if (creditQuantityMinor != null && creditQuantityMinor <= 0) throw new IllegalArgumentException("credit quantity must be positive");
        if (seatQuantity != null && seatQuantity <= 0) throw new IllegalArgumentException("seat quantity must be positive");
    }
}
