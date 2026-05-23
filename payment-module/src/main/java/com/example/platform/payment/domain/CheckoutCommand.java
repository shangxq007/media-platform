package com.example.platform.payment.domain;

public record CheckoutCommand(
        String checkoutSessionId,
        String canonicalProductCode,
        String successUrl,
        String cancelUrl,
        String tenantId,
        String userId,
        Long amountMinor,
        String currencyCode) {

    public CheckoutCommand(String checkoutSessionId, String canonicalProductCode,
                           String successUrl, String cancelUrl) {
        this(checkoutSessionId, canonicalProductCode, successUrl, cancelUrl, null, null, null, null);
    }
}
