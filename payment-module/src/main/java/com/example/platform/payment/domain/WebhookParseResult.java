package com.example.platform.payment.domain;

public record WebhookParseResult(
        String eventType,
        int eventVersion,
        String externalReference,
        boolean validSignature,
        String canonicalStatus,
        String checkoutSessionId,
        String tenantId,
        String userId) {

    public WebhookParseResult(String eventType, int eventVersion, String externalReference, boolean validSignature) {
        this(eventType, eventVersion, externalReference, validSignature, null, null, null, null);
    }

    public boolean paymentSucceeded() {
        return validSignature
                && ("payment.succeeded".equals(eventType) || "payment_intent.succeeded".equals(eventType))
                && checkoutSessionId != null
                && !checkoutSessionId.isBlank();
    }
}
