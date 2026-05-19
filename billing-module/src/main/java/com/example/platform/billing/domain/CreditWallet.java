package com.example.platform.billing.domain;

import java.time.Instant;

public record CreditWallet(
        String walletId,
        String tenantId,
        String workspaceId,
        String userId,
        long balanceMinor,
        String currencyCode,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
