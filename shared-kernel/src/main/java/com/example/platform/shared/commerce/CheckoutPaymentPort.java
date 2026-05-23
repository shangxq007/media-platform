package com.example.platform.shared.commerce;

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
            String cartId) {
        public CheckoutPaymentRequest(
                String checkoutSessionId,
                String tenantId,
                String userId,
                String productCode,
                long amountMinor,
                String currencyCode,
                String successUrl,
                String cancelUrl) {
            this(checkoutSessionId, tenantId, userId, productCode, amountMinor,
                    currencyCode, successUrl, cancelUrl, null);
        }
    }

    record CheckoutPaymentSession(
            String providerCode,
            String providerReference,
            String redirectUrl) {
    }
}
