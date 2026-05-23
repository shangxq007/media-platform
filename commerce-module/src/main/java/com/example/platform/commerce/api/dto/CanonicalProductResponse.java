package com.example.platform.commerce.api.dto;

public record CanonicalProductResponse(
        String productCode,
        String purchaseMode,
        String lineType,
        String featureBundleCode,
        String quotaProfileCode,
        String planKey,
        String tierKey,
        String bundleKey,
        Long creditAmountMinor,
        Integer includedSeats,
        String seatFeatureKey,
        long priceMinor,
        String currencyCode,
        String displayName) {
}
