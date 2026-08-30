package com.example.platform.payment.domain;

import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import java.time.Instant;

public record InitiateCheckoutCommand(
        String transactionId, PrincipalRef principal, String orderId, String checkoutSessionId,
        String providerCode, Money amount, String productReference, String successUrl,
        String cancelUrl, String idempotencyKey, String source, String reason,
        String traceId, Instant occurredAt) {
    public InitiateCheckoutCommand {
        transactionId = PaymentFingerprints.required(transactionId, "transactionId");
        if (principal == null) throw new IllegalArgumentException("principal is required");
        checkoutSessionId = PaymentFingerprints.required(checkoutSessionId, "checkoutSessionId");
        providerCode = PaymentFingerprints.required(providerCode, "providerCode");
        if (amount == null || amount.amountMinor() <= 0) throw new IllegalArgumentException("positive amount is required");
        productReference = PaymentFingerprints.required(productReference, "productReference");
        idempotencyKey = PaymentFingerprints.required(idempotencyKey, "idempotencyKey");
        source = PaymentFingerprints.required(source, "source");
        reason = PaymentFingerprints.required(reason, "reason");
        traceId = PaymentFingerprints.required(traceId, "traceId");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt is required");
    }
    public String fingerprint() {
        return PaymentFingerprints.sha256("INITIATE", PaymentFingerprints.principal(principal),
                transactionId, PaymentFingerprints.optional(orderId), checkoutSessionId, providerCode,
                PaymentFingerprints.money(amount), productReference,
                PaymentFingerprints.optional(successUrl), PaymentFingerprints.optional(cancelUrl),
                source, reason, traceId, occurredAt.toString());
    }
}
