package com.example.platform.payment.infrastructure;

import com.example.platform.payment.domain.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "platform.payment.hyperswitch", name = "enabled", havingValue = "false", matchIfMissing = false)
public class NoopHyperswitchPaymentProvider implements PaymentProvider {
    @Override
    public ProviderCode code() {
        return new ProviderCode("hyperswitch");
    }

    @Override
    public CheckoutResult createCheckout(CheckoutCommand command) {
        String reference = "hs-" + command.checkoutSessionId();
        return new CheckoutResult(reference, command.successUrl() != null ? command.successUrl() : "/checkout/success");
    }

    @Override
    public PaymentVerificationResult verifyPayment(VerifyPaymentCommand command) {
        return new PaymentVerificationResult(true, "authorized", "paid");
    }

    @Override
    public WebhookParseResult parseWebhook(Map<String, String> headers, String body) {
        WebhookParseResult parsed = WebhookPayloadSupport.parseCommerceWebhook(body, "hs-demo");
        if (parsed.checkoutSessionId() != null) {
            return parsed;
        }
        return new WebhookParseResult("payment.succeeded", 1, "hs-demo", true, "paid", null, null, null);
    }
}
