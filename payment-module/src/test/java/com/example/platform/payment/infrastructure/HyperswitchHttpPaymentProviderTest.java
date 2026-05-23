package com.example.platform.payment.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.payment.domain.VerifyPaymentCommand;
import com.example.platform.payment.domain.WebhookParseResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HyperswitchHttpPaymentProviderTest {

    @Test
    void propertiesEnabledRequiresApiKey() {
        HyperswitchPaymentProperties props = new HyperswitchPaymentProperties();
        props.setEnabled(true);
        assertFalse(props.isEnabled());
        props.setApiKey("sk_test");
        assertTrue(props.isEnabled());
    }

    @Test
    void parseWebhookExtractsSucceededPayment() {
        HyperswitchPaymentProperties props = new HyperswitchPaymentProperties();
        props.setEnabled(true);
        props.setApiKey("test");
        HyperswitchHttpPaymentProvider provider = new HyperswitchHttpPaymentProvider(props);

        String body = """
                {
                  "payment_id": "pay_abc123",
                  "status": "succeeded",
                  "metadata": {
                    "checkout_session_id": "chk_99",
                    "tenant_id": "ten_1",
                    "user_id": "usr_1"
                  }
                }
                """;
        WebhookParseResult result = provider.parseWebhook(Map.of(), body);

        assertEquals("payment.succeeded", result.eventType());
        assertTrue(result.paymentSucceeded());
        assertEquals("chk_99", result.checkoutSessionId());
        assertEquals("ten_1", result.tenantId());
    }

    @Test
    void verifyPaymentReturnsFalseForMissingReference() {
        HyperswitchPaymentProperties props = new HyperswitchPaymentProperties();
        props.setEnabled(true);
        props.setApiKey("test");
        HyperswitchHttpPaymentProvider provider = new HyperswitchHttpPaymentProvider(props);

        var result = provider.verifyPayment(new VerifyPaymentCommand("", "{}"));

        assertFalse(result.verified());
    }
}
