package com.example.platform.payment.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.payment.domain.PaymentState;
import org.junit.jupiter.api.Test;

class WebhookPayloadSupportTest {
    @Test
    void usesProviderEventIdentityAndNestedTransactionReference() {
        var parsed = WebhookPayloadSupport.parseCommerceWebhook("""
                {"id":"evt_1","type":"checkout.session.completed","created":1700000000,
                 "data":{"object":{"id":"cs_1","payment_status":"paid",
                 "metadata":{"checkout_session_id":"checkout-1"}}}}
                """);
        assertEquals("evt_1", parsed.eventId());
        assertEquals("cs_1", parsed.providerReference());
        assertEquals(PaymentState.SETTLED, parsed.canonicalState());
        assertEquals("checkout-1", parsed.checkoutSessionId());
    }

    @Test
    void unpaidStatusNeverMatchesPaidSubstring() {
        var parsed = WebhookPayloadSupport.parseCommerceWebhook("""
                {"id":"evt_unpaid","type":"checkout.session.updated","created":1700000001,
                 "data":{"object":{"id":"cs_unpaid","payment_status":"unpaid"}}}
                """);
        assertEquals(PaymentState.PENDING, parsed.canonicalState());
    }

    @Test
    void missingProviderEventIdentityFailsClosed() {
        assertThrows(IllegalArgumentException.class, () ->
                WebhookPayloadSupport.parseCommerceWebhook(
                        "{\"type\":\"payment.succeeded\",\"providerReference\":\"pi_1\"}"));
    }
}
