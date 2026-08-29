package com.example.platform.payment.domain;

public record ProviderRefundResult(boolean succeeded, String providerRefundReference, String externalState) {
    public ProviderRefundResult {
        if (succeeded) {
            providerRefundReference = PaymentFingerprints.required(
                    providerRefundReference, "providerRefundReference");
        }
        externalState = PaymentFingerprints.required(externalState, "externalState");
    }
}
