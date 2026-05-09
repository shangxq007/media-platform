package com.example.platform.payment.infrastructure;

import com.example.platform.payment.domain.*;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NoopHyperswitchPaymentProvider implements PaymentProvider {
    @Override
    public ProviderCode code() {
        return new ProviderCode("hyperswitch");
    }

    @Override
    public CheckoutResult createCheckout(CheckoutCommand command) {
        return new CheckoutResult("hs-demo", command.successUrl());
    }

    @Override
    public PaymentVerificationResult verifyPayment(VerifyPaymentCommand command) {
        return new PaymentVerificationResult(true, "authorized", "processing");
    }

    @Override
    public WebhookParseResult parseWebhook(Map<String, String> headers, String body) {
        return new WebhookParseResult("payment.updated", 1, "hs-demo", true);
    }
}
