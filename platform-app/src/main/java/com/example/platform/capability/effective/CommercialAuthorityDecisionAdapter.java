package com.example.platform.capability.effective;

import com.example.platform.shared.commercial.CommercialEvidenceRef;
import com.example.platform.shared.commercial.EntitlementDecision;
import com.example.platform.shared.commercial.QuotaDecision;
import java.util.List;
import java.util.Objects;

/** Neutral application adapters that retain the source commercial decisions verbatim. */
public final class CommercialAuthorityDecisionAdapter {

    private CommercialAuthorityDecisionAdapter() {
    }

    public static AuthorityDecisionInput fromEntitlement(EntitlementDecision decision) {
        Objects.requireNonNull(decision, "decision must not be null");
        return new AuthorityDecisionInput(
                EffectiveCapabilitySource.H5_ENTITLEMENT,
                "Entitlement",
                decision.entitlementKey(),
                result(decision.allowed()),
                List.of(decision.reason().name()),
                decision.authorityVersion(),
                decision.traceId(),
                decision.decidedAt(),
                provenance(decision.evidence()),
                false,
                decision.principal(),
                null);
    }

    public static AuthorityDecisionInput fromQuota(QuotaDecision decision) {
        Objects.requireNonNull(decision, "decision must not be null");
        return new AuthorityDecisionInput(
                EffectiveCapabilitySource.H5_COMMERCIAL_QUOTA,
                "Quota",
                decision.quotaKey(),
                result(decision.allowed()),
                List.of(decision.reason().name()),
                decision.authorityVersion(),
                decision.traceId(),
                decision.decidedAt(),
                provenance(decision.evidence()),
                false,
                decision.principal(),
                new QuotaDecisionDetails(
                        decision.quotaKey(),
                        decision.limitUnits(),
                        decision.usedUnits(),
                        decision.requestedUnits()));
    }

    private static AuthorityDecisionResult result(boolean allowed) {
        return allowed ? AuthorityDecisionResult.ALLOW : AuthorityDecisionResult.DENY;
    }

    private static List<AuthorityProvenance> provenance(List<CommercialEvidenceRef> evidence) {
        return evidence.stream()
                .map(reference -> new AuthorityProvenance(
                        reference.authority(), reference.evidenceType(), reference.evidenceId()))
                .toList();
    }
}
