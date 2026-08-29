package com.example.platform.shared.commercial;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Quota authority result, deliberately separate from entitlement and runtime capacity. */
public record QuotaDecision(
        PrincipalRef principal,
        String quotaKey,
        long requestedUnits,
        long limitUnits,
        long usedUnits,
        boolean allowed,
        CommercialDecisionReason reason,
        List<CommercialEvidenceRef> evidence,
        String authorityVersion,
        String traceId,
        Instant decidedAt) {

    public QuotaDecision {
        Objects.requireNonNull(principal, "principal must not be null");
        quotaKey = CommercialValidation.requireNonBlank(quotaKey, "quotaKey");
        Objects.requireNonNull(reason, "reason must not be null");
        evidence = CommercialValidation.immutableEvidence(evidence);
        authorityVersion = CommercialValidation.requireNonBlank(authorityVersion, "authorityVersion");
        traceId = CommercialValidation.requireNonBlank(traceId, "traceId");
        Objects.requireNonNull(decidedAt, "decidedAt must not be null");
        CommercialValidation.requireAllowedReasonConsistency(allowed, reason);
    }
}
