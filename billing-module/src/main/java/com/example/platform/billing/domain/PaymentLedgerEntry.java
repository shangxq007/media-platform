package com.example.platform.billing.domain;

import java.time.OffsetDateTime;

/**
 * Payment ledger entry.
 */
public record PaymentLedgerEntry(
        String entryId,
        String tenantId,
        String paymentId,
        String providerCode,
        double amount,
        String currency,
        String transactionType,
        String referenceId,
        OffsetDateTime transactionAt,
        String status) {

    public static final String TYPE_CHARGE = "CHARGE";
    public static final String TYPE_REFUND = "REFUND";
    public static final String TYPE_ADJUSTMENT = "ADJUSTMENT";
}
