package com.example.platform.payment.domain;

import com.example.platform.shared.commercial.PrincipalRef;
import java.time.Instant;

public record VerifyPaymentCommand(
        PrincipalRef principal, String transactionId, String providerCode,
        String providerReference, long expectedVersion, String idempotencyKey,
        String source, String reason, String traceId, Instant occurredAt) {
    public VerifyPaymentCommand {
        if (principal == null) throw new IllegalArgumentException("principal is required");
        transactionId = PaymentFingerprints.required(transactionId, "transactionId");
        providerCode = PaymentFingerprints.required(providerCode, "providerCode");
        providerReference = PaymentFingerprints.required(providerReference, "providerReference");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must not be negative");
        idempotencyKey = PaymentFingerprints.required(idempotencyKey, "idempotencyKey");
        source = PaymentFingerprints.required(source, "source");
        reason = PaymentFingerprints.required(reason, "reason");
        traceId = PaymentFingerprints.required(traceId, "traceId");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt is required");
    }
    public String fingerprint() {
        return PaymentFingerprints.sha256("VERIFY", PaymentFingerprints.principal(principal),
                transactionId, providerCode, providerReference, Long.toString(expectedVersion),
                source, reason, traceId, occurredAt.toString());
    }
}
