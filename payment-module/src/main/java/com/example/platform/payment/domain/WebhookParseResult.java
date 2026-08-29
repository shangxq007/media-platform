package com.example.platform.payment.domain;

import java.time.Instant;

/** Safe parsed provider fields; the raw body remains only at the adapter edge. */
public record WebhookParseResult(
        String eventId, String eventType, long eventCursor, String providerReference,
        PaymentState canonicalState, Instant occurredAt, String checkoutSessionId,
        String tenantId, String userId) {
}
