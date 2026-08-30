package com.example.platform.payment.domain;

import com.example.platform.shared.commercial.PrincipalRef;
import java.time.Instant;

public record ApplyWebhookCommand(
        PrincipalRef principal, String transactionId, String providerCode, String eventId,
        String providerReference, String eventType, long eventCursor, PaymentState state,
        String payloadSha256, long expectedVersion, String source, String reason, String traceId,
        Instant occurredAt, Instant receivedAt, boolean signatureVerified) {
    public ApplyWebhookCommand {
        if (principal == null) throw new IllegalArgumentException("principal is required");
        transactionId = PaymentFingerprints.required(transactionId, "transactionId");
        providerCode = PaymentFingerprints.required(providerCode, "providerCode");
        eventId = PaymentFingerprints.required(eventId, "eventId");
        providerReference = PaymentFingerprints.required(providerReference, "providerReference");
        eventType = PaymentFingerprints.required(eventType, "eventType");
        if (eventCursor < 0) throw new IllegalArgumentException("eventCursor must not be negative");
        if (state == null) throw new IllegalArgumentException("state is required");
        payloadSha256 = PaymentFingerprints.required(payloadSha256, "payloadSha256");
        source = PaymentFingerprints.required(source, "source");
        reason = PaymentFingerprints.required(reason, "reason");
        traceId = PaymentFingerprints.required(traceId, "traceId");
        if (occurredAt == null || receivedAt == null) throw new IllegalArgumentException("webhook times are required");
        if (!signatureVerified) throw new IllegalArgumentException("verified webhook signature is required");
    }
    public String fingerprint() {
        return PaymentFingerprints.sha256(providerCode, eventId, providerReference, eventType,
                Long.toString(eventCursor), state.name(), payloadSha256);
    }
}
