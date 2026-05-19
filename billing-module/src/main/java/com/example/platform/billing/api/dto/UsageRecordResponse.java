package com.example.platform.billing.api.dto;

import java.time.Instant;

public record UsageRecordResponse(
        String recordId,
        String tenantId,
        String workspaceId,
        String userId,
        String meterKey,
        double quantity,
        String unit,
        Instant recordedAt,
        String idempotencyKey) {
}
