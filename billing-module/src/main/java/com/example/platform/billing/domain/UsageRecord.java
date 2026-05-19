package com.example.platform.billing.domain;

import java.time.Instant;

public record UsageRecord(
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
