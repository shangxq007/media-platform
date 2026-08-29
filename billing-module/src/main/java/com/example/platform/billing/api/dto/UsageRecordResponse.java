package com.example.platform.billing.api.dto;

import java.time.Instant;

public record UsageRecordResponse(
        String recordId,
        String tenantId,
        String meterKey,
        long quantity,
        String unit,
        Instant recordedAt) {
}
