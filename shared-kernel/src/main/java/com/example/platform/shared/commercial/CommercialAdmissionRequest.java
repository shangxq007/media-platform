package com.example.platform.shared.commercial;

import java.time.Instant;
import java.util.Objects;

/** Neutral request envelope for the canonical H5 application admission boundary. */
public record CommercialAdmissionRequest(
        PrincipalRef principal,
        String action,
        String entitlementKey,
        String quotaKey,
        long requestedUnits,
        Instant periodStart,
        Instant periodEnd,
        String traceId,
        Instant decidedAt) {

    public CommercialAdmissionRequest {
        Objects.requireNonNull(principal, "principal must not be null");
        action = CommercialValidation.requireNonBlank(action, "action");
        entitlementKey = CommercialValidation.requireNonBlank(entitlementKey, "entitlementKey");
        quotaKey = CommercialValidation.requireNonBlank(quotaKey, "quotaKey");
        if (requestedUnits <= 0) throw new IllegalArgumentException("requestedUnits must be positive");
        Objects.requireNonNull(periodStart, "periodStart must not be null");
        Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        if (!periodEnd.isAfter(periodStart)) throw new IllegalArgumentException("periodEnd must be after periodStart");
        traceId = CommercialValidation.requireNonBlank(traceId, "traceId");
        Objects.requireNonNull(decidedAt, "decidedAt must not be null");
    }
}
