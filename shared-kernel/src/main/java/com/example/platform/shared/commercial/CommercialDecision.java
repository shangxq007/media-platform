package com.example.platform.shared.commercial;

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
        Instant decidedAt) {

    public CommercialDecision {
        Objects.requireNonNull(principal, "principal must not be null");
        action = CommercialValidation.requireNonBlank(action, "action");
        Objects.requireNonNull(reason, "reason must not be null");
        evidence = CommercialValidation.immutableEvidence(evidence);
        authorityVersion = CommercialValidation.requireNonBlank(authorityVersion, "authorityVersion");
        traceId = CommercialValidation.requireNonBlank(traceId, "traceId");
        Objects.requireNonNull(decidedAt, "decidedAt must not be null");
        CommercialValidation.requireAllowedReasonConsistency(allowed, reason);
    }
}
