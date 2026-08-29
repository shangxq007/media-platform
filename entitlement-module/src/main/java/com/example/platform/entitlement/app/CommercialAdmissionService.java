package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.AccessDecision;
import com.example.platform.shared.commercial.CommercialAdmissionPort;
import com.example.platform.shared.commercial.CommercialAdmissionRequest;
import com.example.platform.shared.commercial.CommercialDecision;
import com.example.platform.shared.commercial.CommercialDecisionReason;
import com.example.platform.shared.commercial.CommercialEvidenceRef;
import com.example.platform.shared.commercial.QuotaDecision;
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
    public CommercialDecision decide(CommercialAdmissionRequest request) {
        try {
            AccessDecision entitlement = entitlements.checkFeature(
                    request.principal(), request.entitlementKey());
            if (!entitlement.allowed()) {
                return denied(request, CommercialDecisionReason.NOT_ENTITLED,
                        new CommercialEvidenceRef("Entitlement", "DENIAL",
                                nonBlank(entitlement.reasonCode(), "no-grant")));
            }

            QuotaDecision quota = quotaDecisions.evaluate(
                    request.principal(), request.quotaKey(),
                    request.periodStart(), request.periodEnd(), request.requestedUnits(),
                    request.traceId(), request.decidedAt());
            List<CommercialEvidenceRef> evidence = new ArrayList<>();
            evidence.add(new CommercialEvidenceRef("Entitlement", "GRANT",
                    nonBlank(entitlement.matchedGrantId(), entitlement.reasonCode())));
            evidence.addAll(quota.evidence());
            if (quota.evidence().isEmpty()) {
                evidence.add(new CommercialEvidenceRef("Quota", "DECISION", quota.authorityVersion()));
            }
            return new CommercialDecision(
                    request.principal(), request.action(), quota.allowed(),
                    quota.allowed() ? CommercialDecisionReason.ALLOWED : CommercialDecisionReason.QUOTA_EXCEEDED,
                    evidence, AUTHORITY_VERSION, request.traceId(), request.decidedAt());
        } catch (RuntimeException unavailable) {
            return denied(request, CommercialDecisionReason.POLICY_DENIED,
                    new CommercialEvidenceRef("CommercialAdmission", "AUTHORITY_UNAVAILABLE",
                            unavailable.getClass().getSimpleName()));
        }
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
