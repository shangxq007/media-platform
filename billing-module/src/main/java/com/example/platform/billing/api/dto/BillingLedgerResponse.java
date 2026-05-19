package com.example.platform.billing.api.dto;

import java.time.Instant;

public record BillingLedgerResponse(
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
}
