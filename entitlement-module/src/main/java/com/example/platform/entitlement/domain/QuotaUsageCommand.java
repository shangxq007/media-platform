package com.example.platform.entitlement.domain;

import com.example.platform.shared.commercial.PrincipalRef;
import java.time.Instant;
import java.util.Objects;

/** Canonical, tenant-scoped and idempotent quota mutation command. */
public record QuotaUsageCommand(
        PrincipalRef principal,
        String quotaKey,
        Instant periodStart,
        Instant periodEnd,
        long signedDelta,
        long limitValue,
        String idempotencyKey,
        QuotaOperationKind operationKind,
        String traceId,
        String reason,
        Instant occurredAt) {

    public QuotaUsageCommand {
        Objects.requireNonNull(principal, "principal must not be null");
        quotaKey = requireNonBlank(quotaKey, "quotaKey");
        Objects.requireNonNull(periodStart, "periodStart must not be null");
        Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        if (!periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("periodEnd must be after periodStart");
        }
        if (limitValue < 0) {
            throw new IllegalArgumentException("limitValue must not be negative");
        }
        idempotencyKey = requireNonBlank(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(operationKind, "operationKind must not be null");
        traceId = requireNonBlank(traceId, "traceId");
        reason = requireNonBlank(reason, "reason");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null/blank");
        }
        return value;
    }
}
