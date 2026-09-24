package com.example.platform.entitlement.api.commercial;

import com.example.platform.shared.commercial.PrincipalRef;

import java.time.Instant;
import java.util.Objects;
import java.math.BigDecimal;

/** Neutral request envelope for the canonical H5 application admission boundary. */
public record CommercialAdmissionRequest(
        PrincipalRef principal,
        String action,
        String entitlementKey,
        String quotaKey,
        BigDecimal requestedUnits,
        Instant periodStart,
        Instant periodEnd,
        String traceId,
        Instant decidedAt) {

    public CommercialAdmissionRequest {
        Objects.requireNonNull(principal, "principal must not be null");
        action = AdmissionInvariants.requireNonBlank(action, "action");
        entitlementKey = AdmissionInvariants.requireNonBlank(entitlementKey, "entitlementKey");
        quotaKey = AdmissionInvariants.requireNonBlank(quotaKey, "quotaKey");
        requestedUnits = com.example.platform.entitlement.domain.QuotaQuantity.exact(requestedUnits, "requestedUnits");
        if (requestedUnits.signum() <= 0) throw new IllegalArgumentException("requestedUnits must be positive");
        Objects.requireNonNull(periodStart, "periodStart must not be null");
        Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        if (!periodEnd.isAfter(periodStart)) throw new IllegalArgumentException("periodEnd must be after periodStart");
        traceId = AdmissionInvariants.requireNonBlank(traceId, "traceId");
        Objects.requireNonNull(decidedAt, "decidedAt must not be null");
    }

    public CommercialAdmissionRequest(PrincipalRef principal, String action, String entitlementKey,
            String quotaKey, long requestedUnits, Instant periodStart, Instant periodEnd,
            String traceId, Instant decidedAt) {
        this(principal, action, entitlementKey, quotaKey, BigDecimal.valueOf(requestedUnits),
                periodStart, periodEnd, traceId, decidedAt);
    }
}
