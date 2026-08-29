package com.example.platform.payment.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.payment.app.PaymentTransactionAuthority;
import com.example.platform.payment.domain.ApplyWebhookCommand;
import com.example.platform.payment.domain.PaymentProvider;
import com.example.platform.payment.domain.PaymentState;
import com.example.platform.payment.domain.PaymentTransaction;
import com.example.platform.payment.domain.ProviderCode;
import com.example.platform.payment.domain.WebhookParseResult;
import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentWebhookAdapterTest {
    private PaymentTransactionAuthority authority;
    private PaymentProvider provider;
    private PaymentWebhookAdapter adapter;
    private StripePaymentProperties stripe;

    @BeforeEach
    void setUp() {
        authority = mock(PaymentTransactionAuthority.class);
        provider = mock(PaymentProvider.class);
        when(provider.code()).thenReturn(new ProviderCode("stripe"));
        PaymentWebhookProperties webhook = new PaymentWebhookProperties();
        webhook.setAllowUnsigned(false);
        stripe = new StripePaymentProperties();
        stripe.setWebhookSecret("whsec_test");
        adapter = new PaymentWebhookAdapter(List.of(provider), authority, webhook, stripe,
                new HyperswitchPaymentProperties());
    }

    @Test
    void invalidSignatureIsRejectedBeforeParsingOrWriting() {
        assertThrows(IllegalArgumentException.class,
                () -> adapter.handle("stripe", Map.of(), "{\"id\":\"evt_1\"}"));
        verify(provider, never()).parseWebhook(any(), any());
        verify(authority, never()).applyWebhook(any());
    }

    @Test
    void validSignatureUsesProviderEventIdentityAndDurableBinding() throws Exception {
        String body = "{\"id\":\"evt_1\"}";
        long timestamp = System.currentTimeMillis() / 1000L;
        Map<String, String> headers = Map.of("Stripe-Signature",
                "t=" + timestamp + ",v1=" + hmac("whsec_test", timestamp + "." + body));
        WebhookParseResult parsed = new WebhookParseResult("evt_1", "payment.succeeded", 7,
                "pi_1", PaymentState.SETTLED, Instant.EPOCH, null, null, null);
        PaymentTransaction transaction = transaction();
        when(provider.parseWebhook(headers, body)).thenReturn(parsed);
        when(authority.findByProviderReference("stripe", "pi_1")).thenReturn(Optional.of(transaction));
        when(authority.applyWebhook(any(ApplyWebhookCommand.class))).thenReturn(transaction);

        assertEquals(transaction, adapter.handle("stripe", headers, body));
        verify(authority).applyWebhook(any(ApplyWebhookCommand.class));
    }

    @Test
    void unknownProviderFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> adapter.handle("unknown", Map.of(), "{}"));
    }

    private static PaymentTransaction transaction() {
        PrincipalRef principal = PrincipalRef.tenantScoped("tenant-1", PrincipalType.USER, "user-1");
        return new PaymentTransaction("txn-1", principal, "order-1", "checkout-1", "stripe",
                "pi_1", "https://pay", new Money(100, "USD"), PaymentState.PENDING,
                null, new Money(0, "USD"), new Money(0, "USD"), 2, "commerce", "trace",
                Instant.EPOCH, Instant.EPOCH);
    }

    private static String hmac(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(
                payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
