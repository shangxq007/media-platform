package com.example.platform.payment.domain;

import java.util.Map;

public interface PaymentProvider {
    ProviderCode code();
    CheckoutResult createCheckout(CheckoutCommand command);
    PaymentVerificationResult verifyPayment(VerifyPaymentCommand command);
    WebhookParseResult parseWebhook(Map<String, String> headers, String body);
}
