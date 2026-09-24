package com.example.platform.entitlement.api.commercial;

import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.CommercialEvidenceRef;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.math.BigDecimal;

/** Quota authority result, deliberately separate from entitlement and runtime capacity. */
public record QuotaDecision(
        PrincipalRef principal,
        String quotaKey,
        BigDecimal requestedUnits,
        BigDecimal limitUnits,
        BigDecimal usedUnits,
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
        requestedUnits = com.example.platform.entitlement.domain.QuotaQuantity.exact(requestedUnits, "requestedUnits");
        limitUnits = com.example.platform.entitlement.domain.QuotaQuantity.exact(limitUnits, "limitUnits");
        usedUnits = com.example.platform.entitlement.domain.QuotaQuantity.exact(usedUnits, "usedUnits");
        AdmissionInvariants.requireAllowedReasonConsistency(allowed, reason);
    }

    public QuotaDecision(PrincipalRef principal, String quotaKey, long requestedUnits,
            long limitUnits, long usedUnits, boolean allowed, CommercialDecisionReason reason,
            List<CommercialEvidenceRef> evidence, String authorityVersion, String traceId,
            Instant decidedAt) {
        this(principal, quotaKey, BigDecimal.valueOf(requestedUnits), BigDecimal.valueOf(limitUnits),
                BigDecimal.valueOf(usedUnits), allowed, reason, evidence, authorityVersion, traceId, decidedAt);
    }

    public QuotaDecision(PrincipalRef principal, String quotaKey, BigDecimal requestedUnits,
            BigDecimal limitUnits, long usedUnits, boolean allowed, CommercialDecisionReason reason,
            List<CommercialEvidenceRef> evidence, String authorityVersion, String traceId,
            Instant decidedAt) {
        this(principal, quotaKey, requestedUnits, limitUnits, BigDecimal.valueOf(usedUnits), allowed,
                reason, evidence, authorityVersion, traceId, decidedAt);
    }
}
