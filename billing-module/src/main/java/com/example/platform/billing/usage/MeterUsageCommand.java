package com.example.platform.billing.usage;

import java.time.Instant;
import java.util.Objects;

/** Tenant-scoped request to meter one already-persisted observation. */
public record MeterUsageCommand(
        String tenantId,
        String observedUsageId,
        String meteringRuleId,
        String meteringRuleVersion,
        Instant meteredAt,
        String traceId) {

    public MeterUsageCommand {
        tenantId = requireNonBlank(tenantId, "tenantId");
        observedUsageId = requireNonBlank(observedUsageId, "observedUsageId");
        meteringRuleId = requireNonBlank(meteringRuleId, "meteringRuleId");
        meteringRuleVersion = requireNonBlank(meteringRuleVersion, "meteringRuleVersion");
        Objects.requireNonNull(meteredAt, "meteredAt must not be null");
        traceId = requireNonBlank(traceId, "traceId");
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null/blank");
        }
        return value;
    }
}
