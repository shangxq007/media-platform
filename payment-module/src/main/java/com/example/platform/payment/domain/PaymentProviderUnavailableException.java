package com.example.platform.payment.domain;

import com.example.platform.shared.web.ErrorCode;
import com.example.platform.shared.web.PlatformException;

/** Typed HTTP-503 fail-closed outcome for a requested payment provider that is not configured. */
public final class PaymentProviderUnavailableException extends PlatformException {

    private static final ErrorCode PROVIDER_UNAVAILABLE = new ErrorCode() {
        @Override public String code() { return "PAYMENT-503-001"; }
        @Override public String title() { return "Payment provider unavailable"; }
        @Override public int status() { return 503; }
    };

    private final String providerCode;

    public PaymentProviderUnavailableException(String providerCode) {
        super(PROVIDER_UNAVAILABLE, "Payment provider is unavailable: " + providerCode);
        this.providerCode = providerCode;
    }

    public String providerCode() {
        return providerCode;
    }
}
