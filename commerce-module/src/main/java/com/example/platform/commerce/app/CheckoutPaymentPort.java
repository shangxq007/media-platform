package com.example.platform.commerce.app;

import java.time.Instant;

/**
 * Optional payment initiation when a commerce checkout session is created.
 */
public interface CheckoutPaymentPort {

    CheckoutPaymentSession createPaymentForCheckout(CheckoutPaymentRequest request);

    record CheckoutPaymentRequest(
            String checkoutSessionId,
            String tenantId,
            String userId,
            String productCode,
            long amountMinor,
            String currencyCode,
            String successUrl,
            String cancelUrl,
            String cartId,
            String idempotencyKey,
            String traceId,
            Instant occurredAt) {
    }

    record CheckoutPaymentSession(
            String providerCode,
            String providerReference,
            String redirectUrl) {
    }
}
