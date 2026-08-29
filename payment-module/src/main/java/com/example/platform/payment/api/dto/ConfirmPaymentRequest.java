package com.example.platform.payment.api.dto;

import java.time.Instant;

public record ConfirmPaymentRequest(
        String tenantId, String principalType, String principalId, String workspaceId,
        String organizationId, String transactionId, String providerCode,
        String providerReference, long expectedVersion, String idempotencyKey,
        String reason, String traceId, Instant occurredAt) {
}
