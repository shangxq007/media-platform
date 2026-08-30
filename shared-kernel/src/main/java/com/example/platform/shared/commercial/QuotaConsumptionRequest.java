package com.example.platform.shared.commercial;

import java.time.Instant;
import java.util.Objects;

/** Neutral consumption command sent to the sole canonical quota mutation authority. */
public record QuotaConsumptionRequest(
        PrincipalRef principal,
        String quotaKey,
        long amount,
        Instant periodStart,
        Instant periodEnd,
        String idempotencyKey,
        String traceId,
        String reason,
        Instant occurredAt) {

    public QuotaConsumptionRequest {
        Objects.requireNonNull(principal, "principal must not be null");
        quotaKey = CommercialValidation.requireNonBlank(quotaKey, "quotaKey");
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        Objects.requireNonNull(periodStart, "periodStart must not be null");
        Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        if (!periodEnd.isAfter(periodStart)) throw new IllegalArgumentException("periodEnd must be after periodStart");
        idempotencyKey = CommercialValidation.requireNonBlank(idempotencyKey, "idempotencyKey");
        traceId = CommercialValidation.requireNonBlank(traceId, "traceId");
        reason = CommercialValidation.requireNonBlank(reason, "reason");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
