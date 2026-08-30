package com.example.platform.payment.infrastructure;

import com.example.platform.payment.domain.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "platform.payment.stripe", name = "enabled", havingValue = "false", matchIfMissing = false)
public class NoopStripePaymentProvider implements PaymentProvider {
    @Override
    public ProviderCode code() {
        return new ProviderCode("stripe");
    }

    @Override
    public CheckoutResult createCheckout(InitiateCheckoutCommand command) {
        String reference = "stripe-" + command.checkoutSessionId();
        return new CheckoutResult(reference, command.successUrl() != null ? command.successUrl() : "/checkout/success");
    }

    @Override
    public PaymentVerificationResult verifyPayment(ProviderVerificationRequest command) {
        return new PaymentVerificationResult(true, "succeeded", PaymentState.SETTLED);
    }

    @Override
    public ProviderRefundResult refund(ProviderRefundRequest command) {
        return new ProviderRefundResult(true, "stripe-refund-" + command.idempotencyKey(), "succeeded");
    }

    @Override
    public WebhookParseResult parseWebhook(Map<String, String> headers, String body) {
        return WebhookPayloadSupport.parseCommerceWebhook(body);
    }
}
