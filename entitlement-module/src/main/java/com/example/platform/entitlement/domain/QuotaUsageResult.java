package com.example.platform.entitlement.domain;

import com.example.platform.shared.commercial.PrincipalRef;
import java.time.Instant;
import java.util.Objects;

/** Committed quota operation result returned identically for idempotent replays. */
public record QuotaUsageResult(
        String operationId,
        PrincipalRef principal,
        String quotaKey,
        Instant periodStart,
        Instant periodEnd,
        long signedDelta,
        long limitValue,
        String idempotencyKey,
        QuotaOperationKind operationKind,
        QuotaUsageOutcome outcome,
        long usageBefore,
        long usageAfter,
        QuotaUsageRejectionReason rejectionReason,
        String traceId,
        String reason,
        Instant occurredAt,
        Instant recordedAt) {

    public QuotaUsageResult {
        operationId = requireNonBlank(operationId, "operationId");
        Objects.requireNonNull(principal, "principal must not be null");
        quotaKey = requireNonBlank(quotaKey, "quotaKey");
        Objects.requireNonNull(periodStart, "periodStart must not be null");
        Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        idempotencyKey = requireNonBlank(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(operationKind, "operationKind must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        if ((outcome == QuotaUsageOutcome.APPLIED) != (rejectionReason == null)) {
            throw new IllegalArgumentException("rejectionReason is required exactly for rejected results");
        }
        traceId = requireNonBlank(traceId, "traceId");
        reason = requireNonBlank(reason, "reason");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");
    }

    public boolean applied() {
        return outcome == QuotaUsageOutcome.APPLIED;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null/blank");
        }
        return value;
    }
}
