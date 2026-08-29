package com.example.platform.billing.domain;

import com.example.platform.shared.commercial.Money;
import java.time.Instant;

public record CreditTransaction(
        String transactionId, String tenantId, String walletId, String reservationId,
        String transactionType, Money amount, Money balanceAfter,
        String referenceType, String referenceId, String description,
        String idempotencyKey, String payloadFingerprint, Instant createdAt) {
    public static final String TYPE_CREDIT = "CREDIT";
    public static final String TYPE_DEBIT = "DEBIT";
    public static final String TYPE_RESERVE = "RESERVE";
    public static final String TYPE_RELEASE = "RELEASE";
    public static final String TYPE_FINALIZE = "FINALIZE";
    public static final String TYPE_REFUND = "REFUND";
    public long amountMinor() { return amount.amountMinor(); }
    public long balanceAfterMinor() { return balanceAfter.amountMinor(); }
}
