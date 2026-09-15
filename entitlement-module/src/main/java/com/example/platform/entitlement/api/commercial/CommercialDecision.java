package com.example.platform.entitlement.api.commercial;

import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.CommercialEvidenceRef;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Provider-neutral H5 decision with immutable structured authority evidence. */
public record CommercialDecision(
        PrincipalRef principal,
        String action,
        boolean allowed,
        CommercialDecisionReason reason,
        List<CommercialEvidenceRef> evidence,
        String authorityVersion,
        String traceId,
        Instant decidedAt,
        AdmissionGrantFacts grantFacts) {

    public CommercialDecision(PrincipalRef principal, String action, boolean allowed,
            CommercialDecisionReason reason, List<CommercialEvidenceRef> evidence,
            String authorityVersion, String traceId, Instant decidedAt) {
        this(principal, action, allowed, reason, evidence, authorityVersion, traceId, decidedAt, null);
    }

    public CommercialDecision {
        Objects.requireNonNull(principal, "principal must not be null");
        action = AdmissionInvariants.requireNonBlank(action, "action");
        Objects.requireNonNull(reason, "reason must not be null");
        evidence = AdmissionInvariants.immutableEvidence(evidence);
        authorityVersion = AdmissionInvariants.requireNonBlank(authorityVersion, "authorityVersion");
        traceId = AdmissionInvariants.requireNonBlank(traceId, "traceId");
        Objects.requireNonNull(decidedAt, "decidedAt must not be null");
        AdmissionInvariants.requireAllowedReasonConsistency(allowed, reason);
    }
}
