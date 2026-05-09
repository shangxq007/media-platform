package com.example.platform.payment.infrastructure;

import com.example.platform.payment.domain.*;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NoopStripePaymentProvider implements PaymentProvider {
    @Override
    public ProviderCode code() {
        return new ProviderCode("stripe");
    }

    @Override
    public CheckoutResult createCheckout(CheckoutCommand command) {
        return new CheckoutResult("stripe-demo", command.successUrl());
    }

    @Override
    public PaymentVerificationResult verifyPayment(VerifyPaymentCommand command) {
        return new PaymentVerificationResult(true, "succeeded", "paid");
    }

    @Override
    public WebhookParseResult parseWebhook(Map<String, String> headers, String body) {
        return new WebhookParseResult("payment.succeeded", 1, "stripe-demo", true);
    }
}
