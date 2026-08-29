package com.example.platform.payment.domain;

import com.example.platform.shared.commercial.PrincipalRef;
import java.time.Instant;

public record BindProviderResultCommand(
        PrincipalRef principal, String transactionId, String providerCode,
        String providerReference, String redirectUrl, PaymentState state, long expectedVersion,
        String idempotencyKey, String source, String reason, String traceId, Instant occurredAt) {
    public BindProviderResultCommand {
        if (principal == null) throw new IllegalArgumentException("principal is required");
        transactionId = PaymentFingerprints.required(transactionId, "transactionId");
        providerCode = PaymentFingerprints.required(providerCode, "providerCode");
        providerReference = PaymentFingerprints.required(providerReference, "providerReference");
        if (state == null) throw new IllegalArgumentException("state is required");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must not be negative");
        idempotencyKey = PaymentFingerprints.required(idempotencyKey, "idempotencyKey");
        source = PaymentFingerprints.required(source, "source");
        reason = PaymentFingerprints.required(reason, "reason");
        traceId = PaymentFingerprints.required(traceId, "traceId");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt is required");
    }
    public String fingerprint() {
        return PaymentFingerprints.sha256("BIND", PaymentFingerprints.principal(principal),
                transactionId, providerCode, providerReference, PaymentFingerprints.optional(redirectUrl),
                state.name(), Long.toString(expectedVersion), source, reason, traceId, occurredAt.toString());
    }
}
