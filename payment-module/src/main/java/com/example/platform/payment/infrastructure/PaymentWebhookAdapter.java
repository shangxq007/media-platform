package com.example.platform.payment.infrastructure;

import com.example.platform.payment.app.PaymentTransactionAuthority;
import com.example.platform.payment.domain.ApplyWebhookCommand;
import com.example.platform.payment.domain.PaymentProvider;
import com.example.platform.payment.domain.PaymentTransaction;
import com.example.platform.payment.domain.WebhookParseResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Provider edge: verifies signatures and parses raw bodies before any Payment mutation. */
@Component
public class PaymentWebhookAdapter {
    private final Map<String, PaymentProvider> providers;
    private final PaymentTransactionAuthority authority;
    private final PaymentWebhookProperties webhookProperties;
    private final StripePaymentProperties stripeProperties;
    private final HyperswitchPaymentProperties hyperswitchProperties;

    public PaymentWebhookAdapter(List<PaymentProvider> providers, PaymentTransactionAuthority authority,
                                 PaymentWebhookProperties webhookProperties,
                                 StripePaymentProperties stripeProperties,
                                 HyperswitchPaymentProperties hyperswitchProperties) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> provider.code().value(), provider -> provider));
        this.authority = authority;
        this.webhookProperties = webhookProperties;
        this.stripeProperties = stripeProperties;
        this.hyperswitchProperties = hyperswitchProperties;
    }

    public PaymentTransaction handle(String providerCode, Map<String, String> headers, String body) {
        PaymentProvider provider = providers.get(providerCode);
        if (provider == null) throw new IllegalArgumentException("Unknown payment provider: " + providerCode);
        if (!webhookProperties.isAllowUnsigned() && !verifySignature(providerCode, headers, body)) {
            throw new IllegalArgumentException("Webhook signature validation failed for provider " + providerCode);
        }
        WebhookParseResult parsed = provider.parseWebhook(headers, body);
        PaymentTransaction transaction = authority.findByProviderReference(
                        providerCode, parsed.providerReference())
                .orElseThrow(() -> new IllegalStateException("Webhook payment transaction binding not found"));
        Instant receivedAt = Instant.now();
        Instant occurredAt = parsed.occurredAt() == null ? receivedAt : parsed.occurredAt();
        return authority.applyWebhook(new ApplyWebhookCommand(
                transaction.principal(), transaction.transactionId(), providerCode,
                parsed.eventId(), parsed.providerReference(), parsed.eventType(), parsed.eventCursor(),
                parsed.canonicalState(), sha256(body), transaction.version(), "provider-webhook",
                "verified provider event", "webhook:" + providerCode + ":" + parsed.eventId(),
                occurredAt, receivedAt, true));
    }

    private boolean verifySignature(String providerCode, Map<String, String> headers, String body) {
        if ("stripe".equals(providerCode)) {
            return StripeWebhookSignatureVerifier.verify(headers, body, stripeProperties.getWebhookSecret());
        }
        if ("hyperswitch".equals(providerCode)) {
            return HyperswitchWebhookSignatureVerifier.verify(headers, body,
                    hyperswitchProperties.getWebhookSecret());
        }
        return false;
    }

    private static String sha256(String body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
