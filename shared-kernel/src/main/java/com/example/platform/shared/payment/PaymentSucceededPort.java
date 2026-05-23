package com.example.platform.shared.payment;

/**
 * Invoked after a provider webhook is parsed as a successful payment.
 */
public interface PaymentSucceededPort {

    void onPaymentSucceeded(PaymentSucceededEvent event);

    record PaymentSucceededEvent(
            String providerCode,
            String providerReference,
            String checkoutSessionId,
            String tenantId,
            String userId,
            String canonicalStatus) {
    }
}
