package com.example.platform.payment.domain;

import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import java.time.Instant;

public record RefundPaymentCommand(
        PrincipalRef principal, String transactionId, String originalCaptureReference,
        Money amount, long expectedVersion, String idempotencyKey, String source,
        String reason, String traceId, Instant occurredAt) {
    public RefundPaymentCommand {
        if (principal == null) throw new IllegalArgumentException("principal is required");
        transactionId = PaymentFingerprints.required(transactionId, "transactionId");
        originalCaptureReference = PaymentFingerprints.required(originalCaptureReference, "originalCaptureReference");
        if (amount == null || amount.amountMinor() <= 0) throw new IllegalArgumentException("positive refund amount is required");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must not be negative");
        idempotencyKey = PaymentFingerprints.required(idempotencyKey, "idempotencyKey");
        source = PaymentFingerprints.required(source, "source");
        reason = PaymentFingerprints.required(reason, "reason");
        traceId = PaymentFingerprints.required(traceId, "traceId");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt is required");
    }
    public String fingerprint() {
        return PaymentFingerprints.sha256("REFUND", PaymentFingerprints.principal(principal),
                transactionId, originalCaptureReference, PaymentFingerprints.money(amount),
                Long.toString(expectedVersion), source, reason, traceId, occurredAt.toString());
    }
}
