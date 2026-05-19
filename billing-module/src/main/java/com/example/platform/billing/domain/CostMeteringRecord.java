package com.example.platform.billing.domain;

import java.time.OffsetDateTime;

/**
 * Base record for all cost metering events.
 */
public record CostMeteringRecord(
        String meteringId,
        String tenantId,
        String userId,
        String resourceType,
        String resourceId,
        String meteringType,
        double quantity,
        String unit,
        OffsetDateTime recordedAt) {
}
