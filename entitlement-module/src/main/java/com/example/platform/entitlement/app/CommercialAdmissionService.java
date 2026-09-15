package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.AccessDecision;
import com.example.platform.entitlement.api.commercial.CommercialAdmissionPort;
import com.example.platform.entitlement.api.commercial.CommercialAdmissionRequest;
import com.example.platform.entitlement.api.commercial.CommercialDecision;
import com.example.platform.entitlement.api.commercial.CommercialDecisionReason;
import com.example.platform.shared.commercial.CommercialEvidenceRef;
import com.example.platform.entitlement.api.commercial.QuotaDecision;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Canonical H5 application admission boundary over distinct Entitlement and Quota authorities. */
@Service
public class CommercialAdmissionService implements CommercialAdmissionPort {
    private static final String AUTHORITY_VERSION = "commercial-admission-v1";

    private final EntitlementService entitlements;
    private final QuotaDecisionService quotaDecisions;

    public CommercialAdmissionService(
            EntitlementService entitlements, QuotaDecisionService quotaDecisions) {
        this.entitlements = entitlements;
        this.quotaDecisions = quotaDecisions;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public CommercialDecision decideForAcceptance(CommercialAdmissionRequest request) {
        var locked = entitlements.lockAdmissionGrants(request.principal());
        // Linearization: use only the rows returned by the locking read, evaluated at this instant.
        var now = java.time.Instant.now();
        var current = new CommercialAdmissionRequest(request.principal(), request.action(), request.entitlementKey(),
                request.quotaKey(), request.requestedUnits(), request.periodStart(), request.periodEnd(),
                request.traceId(), now);
        var grant = locked.stream().filter(g -> "ACTIVE".equals(g.status())
                && request.entitlementKey().equals(g.bundleCode()) && !g.effectiveAt().isAfter(now)
                && (g.expiresAt() == null || g.expiresAt().isAfter(now)))
                .sorted(java.util.Comparator.comparing(com.example.platform.entitlement.domain.EntitlementGrantView::effectiveAt).reversed()
                        .thenComparing(com.example.platform.entitlement.domain.EntitlementGrantView::grantId))
                .findFirst().orElse(null);
        if (grant == null) return denied(current, CommercialDecisionReason.NOT_ENTITLED,
                new CommercialEvidenceRef("Entitlement", "DENIAL", "no-grant"));
        var facts = new com.example.platform.entitlement.api.commercial.AdmissionGrantFacts(grant.grantId(),
                grant.principal(), grant.bundleCode(), grant.version(), grant.effectiveAt(), grant.expiresAt());
        return evaluateQuota(current, grant.grantId(), facts);
    }

    @Override
    public CommercialDecision decide(CommercialAdmissionRequest request) {
        try {
            AccessDecision entitlement = entitlements.checkFeature(request.principal(), request.entitlementKey());
            if (!entitlement.allowed()) return denied(request, CommercialDecisionReason.NOT_ENTITLED,
                    new CommercialEvidenceRef("Entitlement", "DENIAL", nonBlank(entitlement.reasonCode(), "no-grant")));
            return evaluateQuota(request, nonBlank(entitlement.matchedGrantId(), entitlement.reasonCode()), null);
        } catch (RuntimeException unavailable) {
            return unavailable(request, unavailable);
        }
    }

    private CommercialDecision evaluateQuota(CommercialAdmissionRequest request, String grantId,
            com.example.platform.entitlement.api.commercial.AdmissionGrantFacts facts) {
        try {
            QuotaDecision quota = quotaDecisions.evaluate(request.principal(), request.quotaKey(),
                    request.periodStart(), request.periodEnd(), request.requestedUnits(), request.traceId(), request.decidedAt());
            List<CommercialEvidenceRef> evidence = new ArrayList<>();
            evidence.add(new CommercialEvidenceRef("Entitlement", "GRANT", grantId));
            evidence.addAll(quota.evidence());
            if (quota.evidence().isEmpty()) evidence.add(new CommercialEvidenceRef("Quota", "DECISION", quota.authorityVersion()));
            return new CommercialDecision(request.principal(), request.action(), quota.allowed(),
                    quota.allowed() ? CommercialDecisionReason.ALLOWED : CommercialDecisionReason.QUOTA_EXCEEDED,
                    evidence, AUTHORITY_VERSION, request.traceId(), request.decidedAt(), facts);
        } catch (RuntimeException unavailable) {
            return unavailable(request, unavailable);
        }
    }

    private static CommercialDecision unavailable(CommercialAdmissionRequest request, RuntimeException unavailable) {
        return denied(request, CommercialDecisionReason.POLICY_DENIED,
                new CommercialEvidenceRef("CommercialAdmission", "AUTHORITY_UNAVAILABLE", unavailable.getClass().getSimpleName()));
    }

    private static CommercialDecision denied(
            CommercialAdmissionRequest request,
            CommercialDecisionReason reason,
            CommercialEvidenceRef evidence) {
        return new CommercialDecision(request.principal(), request.action(), false, reason,
                List.of(evidence), AUTHORITY_VERSION, request.traceId(), request.decidedAt());
    }

    private static String nonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }
}
