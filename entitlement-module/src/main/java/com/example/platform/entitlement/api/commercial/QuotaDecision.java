package com.example.platform.entitlement.api.commercial;

import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.CommercialEvidenceRef;

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
        quotaKey = AdmissionInvariants.requireNonBlank(quotaKey, "quotaKey");
        Objects.requireNonNull(reason, "reason must not be null");
        evidence = AdmissionInvariants.immutableEvidence(evidence);
        authorityVersion = AdmissionInvariants.requireNonBlank(authorityVersion, "authorityVersion");
        traceId = AdmissionInvariants.requireNonBlank(traceId, "traceId");
        Objects.requireNonNull(decidedAt, "decidedAt must not be null");
        AdmissionInvariants.requireAllowedReasonConsistency(allowed, reason);
    }
}
