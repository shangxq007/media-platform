package com.example.platform.payment.domain;

public record ProviderVerificationRequest(
        String providerReference, String providerIdempotencyKey, String traceId) {
    public ProviderVerificationRequest {
        providerReference = PaymentFingerprints.required(providerReference, "providerReference");
        providerIdempotencyKey = PaymentFingerprints.required(providerIdempotencyKey, "providerIdempotencyKey");
        traceId = PaymentFingerprints.required(traceId, "traceId");
    }
}
