package com.example.platform.entitlement.domain;

import com.example.platform.shared.commercial.PrincipalRef;
import java.time.Instant;
import java.util.Objects;

/** Explicitly tenant/principal/period-scoped quota read or decision request. */
public record QuotaUsageQuery(
        PrincipalRef principal,
        String quotaKey,
        Instant periodStart,
        Instant periodEnd,
        long requestedUnits,
        long limitUnits,
        String traceId,
        Instant decidedAt) {

    public QuotaUsageQuery {
        Objects.requireNonNull(principal, "principal must not be null");
        quotaKey = requireNonBlank(quotaKey, "quotaKey");
        Objects.requireNonNull(periodStart, "periodStart must not be null");
        Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        if (!periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("periodEnd must be after periodStart");
        }
        if (requestedUnits < 0) {
            throw new IllegalArgumentException("requestedUnits must not be negative");
        }
        if (limitUnits < 0) {
            throw new IllegalArgumentException("limitUnits must not be negative");
        }
        traceId = requireNonBlank(traceId, "traceId");
        Objects.requireNonNull(decidedAt, "decidedAt must not be null");
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null/blank");
        }
        return value;
    }
}
