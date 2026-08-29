package com.example.platform.payment.api.dto;

import java.time.Instant;

public record RefundPaymentRequest(
        String tenantId, String principalType, String principalId, String workspaceId,
        String organizationId, String transactionId, String originalCaptureReference,
        long amountMinor, String currencyCode, long expectedVersion,
        String idempotencyKey, String reason, String traceId, Instant occurredAt) {
}
