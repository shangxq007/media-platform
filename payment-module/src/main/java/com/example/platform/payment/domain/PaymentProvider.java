package com.example.platform.payment.domain;

import java.util.Map;

public interface PaymentProvider {
    ProviderCode code();
    CheckoutResult createCheckout(InitiateCheckoutCommand command);
    PaymentVerificationResult verifyPayment(ProviderVerificationRequest command);
    ProviderRefundResult refund(ProviderRefundRequest command);
    WebhookParseResult parseWebhook(Map<String, String> headers, String body);
}
