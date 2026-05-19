package com.example.platform.billing.api.dto;

public record PricingTierDto(
        long upToQuantity,
        long unitPriceMinor,
        long flatFeeMinor) {
}
