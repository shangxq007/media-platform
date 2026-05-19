package com.example.platform.billing.domain;

public record PricingTier(
        long upToQuantity,
        long unitPriceMinor,
        long flatFeeMinor) {
}
