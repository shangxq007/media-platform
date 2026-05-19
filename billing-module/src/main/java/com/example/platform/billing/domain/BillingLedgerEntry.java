package com.example.platform.billing.domain;

import java.time.Instant;

public record BillingLedgerEntry(
        String entryId,
        String tenantId,
        String workspaceId,
        String userId,
        String entryType,
        long amountMinor,
        String currencyCode,
        String referenceType,
        String referenceId,
        String description,
        Instant createdAt) {

    public static final String TYPE_CHARGE = "CHARGE";
    public static final String TYPE_REFUND = "REFUND";
    public static final String TYPE_ADJUSTMENT = "ADJUSTMENT";
    public static final String TYPE_CREDIT = "CREDIT";
    public static final String TYPE_DEBIT = "DEBIT";
    public static final String TYPE_DISCOUNT = "DISCOUNT";
}
