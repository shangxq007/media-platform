package com.example.platform.shared.commerce;

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
        int periodDays) {

    public PurchaseFulfillmentCommand {
        if (periodDays <= 0) {
            periodDays = 30;
        }
    }
}
