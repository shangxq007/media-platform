package com.example.platform.billing.domain;

import java.time.Instant;

public record CreditTransaction(
        String transactionId,
        String walletId,
        String transactionType,
        long amountMinor,
        long balanceAfterMinor,
        String referenceType,
        String referenceId,
        String description,
        Instant createdAt) {

    public static final String TYPE_CREDIT = "CREDIT";
    public static final String TYPE_DEBIT = "DEBIT";
    public static final String TYPE_RESERVE = "RESERVE";
    public static final String TYPE_RELEASE = "RELEASE";
    public static final String TYPE_FINALIZE = "FINALIZE";
    public static final String TYPE_REFUND = "REFUND";
}
