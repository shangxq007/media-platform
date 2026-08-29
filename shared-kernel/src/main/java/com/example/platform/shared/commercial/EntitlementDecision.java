package com.example.platform.shared.commercial;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Entitlement authority result, deliberately separate from quota semantics. */
public record EntitlementDecision(
        PrincipalRef principal,
        String entitlementKey,
        boolean allowed,
        CommercialDecisionReason reason,
        List<CommercialEvidenceRef> evidence,
        String authorityVersion,
        String traceId,
        Instant decidedAt) {

    public EntitlementDecision {
        Objects.requireNonNull(principal, "principal must not be null");
        entitlementKey = CommercialValidation.requireNonBlank(entitlementKey, "entitlementKey");
        Objects.requireNonNull(reason, "reason must not be null");
        evidence = CommercialValidation.immutableEvidence(evidence);
        authorityVersion = CommercialValidation.requireNonBlank(authorityVersion, "authorityVersion");
        traceId = CommercialValidation.requireNonBlank(traceId, "traceId");
        Objects.requireNonNull(decidedAt, "decidedAt must not be null");
        CommercialValidation.requireAllowedReasonConsistency(allowed, reason);
    }
}
