package com.example.platform.payment.domain;

import com.example.platform.shared.commercial.Money;

public record ProviderRefundRequest(
        String providerReference, String originalCaptureReference, Money amount,
        String idempotencyKey, String traceId) {
    public ProviderRefundRequest {
        providerReference = PaymentFingerprints.required(providerReference, "providerReference");
        originalCaptureReference = PaymentFingerprints.required(originalCaptureReference, "originalCaptureReference");
        if (amount == null || amount.amountMinor() <= 0) throw new IllegalArgumentException("positive amount is required");
        idempotencyKey = PaymentFingerprints.required(idempotencyKey, "idempotencyKey");
        traceId = PaymentFingerprints.required(traceId, "traceId");
    }
}
