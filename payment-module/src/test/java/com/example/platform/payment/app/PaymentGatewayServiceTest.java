package com.example.platform.payment.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.payment.domain.*;
import com.example.platform.payment.infrastructure.NoopHyperswitchPaymentProvider;
import com.example.platform.payment.infrastructure.NoopStripePaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class PaymentGatewayServiceTest {

    private PaymentGatewayService service;

    @BeforeEach
    void setUp() {
        List<PaymentProvider> providers = List.of(
                new NoopStripePaymentProvider(),
                new NoopHyperswitchPaymentProvider()
        );
        service = new PaymentGatewayService(providers, null, null);
    }

    @Test
    void createCheckoutWithStripeProviderReturnsResult() {
        CheckoutCommand command = new CheckoutCommand("chk_123", "pro_monthly",
                "https://example.com/success", "https://example.com/cancel");

        CheckoutResult result = service.createCheckout(command);

        assertNotNull(result);
        assertNotNull(result.providerReference());
        assertEquals("https://example.com/success", result.redirectUrl());
    }

    @Test
    void createCheckoutWithHyperswitchProviderReturnsResult() {
        CheckoutCommand command = new CheckoutCommand("chk_123", "hs_product",
                "https://example.com/success", "https://example.com/cancel");

        CheckoutResult result = service.createCheckout(command);

        assertNotNull(result);
        assertNotNull(result.providerReference());
    }

    @Test
    void verifyPaymentWithStripeProviderReturnsVerified() {
        VerifyPaymentCommand command = new VerifyPaymentCommand("stripe-demo", "{}");

        PaymentVerificationResult result = service.verifyPayment(command);

        assertNotNull(result);
        assertTrue(result.verified());
        assertNotNull(result.canonicalStatus());
        assertNotNull(result.externalState());
    }

    @Test
    void verifyPaymentWithHyperswitchProviderReturnsVerified() {
        VerifyPaymentCommand command = new VerifyPaymentCommand("hs-demo", "{}");

        PaymentVerificationResult result = service.verifyPayment(command);

        assertNotNull(result);
        assertTrue(result.verified());
        assertEquals("processing", result.canonicalStatus());
    }

    @Test
    void confirmWithStripeProviderReturnsResult() {
        PaymentVerificationResult result = service.confirm("stripe", "stripe-ref-123", "{}");

        assertNotNull(result);
        assertTrue(result.verified());
        assertEquals("paid", result.canonicalStatus());
    }

    @Test
    void confirmWithHyperswitchProviderReturnsResult() {
        PaymentVerificationResult result = service.confirm("hyperswitch", "hs-ref-123", "{}");

        assertNotNull(result);
        assertTrue(result.verified());
        assertEquals("processing", result.canonicalStatus());
    }

    @Test
    void confirmWithUnknownProviderUsesNoopStripe() {
        PaymentVerificationResult result = service.confirm("unknown", "ref-123", "{}");

        assertNotNull(result);
        assertTrue(result.verified());
    }

    @Test
    void parseWebhookWithStripeProviderReturnsResult() {
        Map<String, String> headers = Map.of("Stripe-Signature", "test-sig");

        WebhookParseResult result = service.parseWebhook("stripe", headers, "{}");

        assertNotNull(result);
        assertEquals("payment.succeeded", result.eventType());
        assertEquals(1, result.eventVersion());
        assertTrue(result.validSignature());
        assertNotNull(result.externalReference());
    }

    @Test
    void parseWebhookWithHyperswitchProviderReturnsResult() {
        Map<String, String> headers = Map.of("X-Hmac-SHA256", "test-hmac");

        WebhookParseResult result = service.parseWebhook("hyperswitch", headers, "{}");

        assertNotNull(result);
        assertEquals("payment.updated", result.eventType());
        assertEquals(1, result.eventVersion());
        assertTrue(result.validSignature());
    }

    @Test
    void parseWebhookWithUnknownProviderUsesNoopStripe() {
        Map<String, String> headers = Map.of();

        WebhookParseResult result = service.parseWebhook("unknown", headers, "{}");

        assertNotNull(result);
        assertEquals("payment.succeeded", result.eventType());
    }

    @Test
    void noopStripeProviderCodeReturnsStripe() {
        NoopStripePaymentProvider provider = new NoopStripePaymentProvider();
        assertEquals("stripe", provider.code().value());
    }

    @Test
    void noopHyperswitchProviderCodeReturnsHyperswitch() {
        NoopHyperswitchPaymentProvider provider = new NoopHyperswitchPaymentProvider();
        assertEquals("hyperswitch", provider.code().value());
    }

    @Test
    void paymentStateProjectedEventCanBeCreated() {
        PaymentStateProjectedEvent event = new PaymentStateProjectedEvent("stripe", "pi_123", "paid");

        assertEquals("stripe", event.providerCode());
        assertEquals("pi_123", event.providerReference());
        assertEquals("paid", event.canonicalStatus());
    }
}
