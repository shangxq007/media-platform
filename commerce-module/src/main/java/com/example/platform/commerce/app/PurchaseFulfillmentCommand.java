package com.example.platform.commerce.app;

import java.time.Instant;

/**
 * Internal command emitted after a purchase order is confirmed.
 * Fulfillment applies billing projection and entitlement side effects per catalog line type.
 */
public record PurchaseFulfillmentCommand(
        String orderId,
        String tenantId,
        String userId,
        String productCode,
        String purchaseMode,
        String lineType,
        String planKey,
        String tierKey,
        String bundleKey,
        String quotaProfileCode,
        Long creditAmountMinor,
        Integer includedSeats,
        String seatFeatureKey,
        int periodDays,
        Instant occurredAt) {

    public PurchaseFulfillmentCommand {
        if (periodDays <= 0) {
            periodDays = 30;
        }
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt is required");
    }
}
