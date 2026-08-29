package com.example.platform.billing.domain;

import com.example.platform.shared.commercial.Money;
import java.time.OffsetDateTime;

/** Legacy Billing reconciliation input; Payment remains authoritative for transaction state. */
public record PaymentLedgerEntry(
        String entryId, String tenantId, String paymentId, String providerCode,
        Money amount, String transactionType, String referenceId,
        OffsetDateTime transactionAt, String status) {
    public static final String TYPE_CHARGE = "CHARGE";
    public static final String TYPE_REFUND = "REFUND";
    public static final String TYPE_ADJUSTMENT = "ADJUSTMENT";
    public String currency() { return amount.currency(); }
}
