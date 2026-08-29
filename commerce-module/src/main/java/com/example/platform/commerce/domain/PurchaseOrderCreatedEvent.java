package com.example.platform.commerce.domain;

import java.time.Instant;

public record PurchaseOrderCreatedEvent(String orderId, String tenantId, String canonicalProductCode, String orderStatus,
        String offeringId, long offeringVersion, AuthorityReference commercialPriceReference,
        long amountMinorSnapshot, String currencyCodeSnapshot) {

    public PurchaseOrderCreatedEvent(String orderId, String tenantId, String canonicalProductCode) {
        this(orderId, tenantId, canonicalProductCode, "CREATED", "unknown", 1,
                new AuthorityReference("unknown", 1), 0, "USD");
    }

    public long orderValueMinor() {
        if ("CANCELLED".equals(orderStatus)) {
            return 0;
        }
        return amountMinorSnapshot;
    }

    public Instant eventTime() {
        return Instant.now();
    }
}
