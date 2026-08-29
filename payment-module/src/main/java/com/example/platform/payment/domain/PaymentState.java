package com.example.platform.payment.domain;

/** Canonical monotonic Payment-owned transaction state. */
public enum PaymentState {
    INITIATED, PENDING, AUTHORIZED, SETTLED, FAILED, CANCELLED, PARTIALLY_REFUNDED, REFUNDED;

    public boolean terminalForProviderProjection() {
        return this == SETTLED || this == FAILED || this == CANCELLED
                || this == PARTIALLY_REFUNDED || this == REFUNDED;
    }
}
