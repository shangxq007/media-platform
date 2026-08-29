package com.example.platform.payment.app;

/** Idempotent consumer port for durable Payment settlement outbox references. */
public interface PaymentSettlementProjectionPort {
    void onPaymentSettled(PaymentSettledEvent event);

    record PaymentSettledEvent(
            String eventId, String transactionId, String tenantId, String providerCode,
            String providerReference, String checkoutSessionId, String traceId) {
    }
}
