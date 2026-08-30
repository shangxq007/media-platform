package com.example.platform.capability.effective;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.platform.shared.commercial.CommercialDecisionReason;
import com.example.platform.shared.commercial.CommercialEvidenceRef;
import com.example.platform.shared.commercial.EntitlementDecision;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.shared.commercial.QuotaDecision;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommercialAuthorityDecisionAdapterTest {

    private static final PrincipalRef PRINCIPAL =
            PrincipalRef.tenantScoped("tenant-1", PrincipalType.USER, "user-1");
    private static final Instant DECIDED_AT = Instant.parse("2026-08-29T02:03:04Z");

    @Test
    void entitlementAdapterPreservesEveryDecisionAuthorityField() {
        List<CommercialEvidenceRef> evidence = List.of(
                new CommercialEvidenceRef("EntitlementGrantAuthority", "GRANT", "grant-7"),
                new CommercialEvidenceRef("SubscriptionProjection", "CONTRACT", "contract-8"));
        EntitlementDecision source = new EntitlementDecision(
                PRINCIPAL,
                "cap.video.export",
                false,
                CommercialDecisionReason.NOT_ENTITLED,
                evidence,
                "entitlement-v12",
                "entitlement-trace-13",
                DECIDED_AT);

        AuthorityDecisionInput adapted =
                CommercialAuthorityDecisionAdapter.fromEntitlement(source);

        assertEquals(EffectiveCapabilitySource.H5_ENTITLEMENT, adapted.source());
        assertEquals("Entitlement", adapted.authorityName());
        assertEquals(source.principal(), adapted.principal());
        assertEquals(source.entitlementKey(), adapted.decisionTarget());
        assertEquals(AuthorityDecisionResult.DENY, adapted.result());
        assertEquals(List.of(source.reason().name()), adapted.reasonCodes());
        assertEquals(source.authorityVersion(), adapted.authorityVersion());
        assertEquals(source.traceId(), adapted.decisionReference());
        assertEquals(source.decidedAt(), adapted.decidedAt());
        assertEquals(List.of(
                new AuthorityProvenance("EntitlementGrantAuthority", "GRANT", "grant-7"),
                new AuthorityProvenance("SubscriptionProjection", "CONTRACT", "contract-8")),
                adapted.provenance());
        assertFalse(adapted.stale());
        assertNull(adapted.quotaDetails());
    }

    @Test
    void quotaAdapterPreservesDecisionFieldsAndLimitUsedRequestedEvidence() {
        List<CommercialEvidenceRef> evidence = List.of(
                new CommercialEvidenceRef("QuotaUsageAuthority", "PERIOD_USAGE", "usage-21"));
        QuotaDecision source = new QuotaDecision(
                PRINCIPAL,
                "render.minutes",
                9,
                100,
                96,
                false,
                CommercialDecisionReason.QUOTA_EXCEEDED,
                evidence,
                "quota-v22",
                "quota-trace-23",
                DECIDED_AT);

        AuthorityDecisionInput adapted = CommercialAuthorityDecisionAdapter.fromQuota(source);

        assertEquals(EffectiveCapabilitySource.H5_COMMERCIAL_QUOTA, adapted.source());
        assertEquals("Quota", adapted.authorityName());
        assertEquals(source.principal(), adapted.principal());
        assertEquals(source.quotaKey(), adapted.decisionTarget());
        assertEquals(AuthorityDecisionResult.DENY, adapted.result());
        assertEquals(List.of(source.reason().name()), adapted.reasonCodes());
        assertEquals(source.authorityVersion(), adapted.authorityVersion());
        assertEquals(source.traceId(), adapted.decisionReference());
        assertEquals(source.decidedAt(), adapted.decidedAt());
        assertEquals(
                new QuotaDecisionDetails(
                        source.quotaKey(),
                        source.limitUnits(),
                        source.usedUnits(),
                        source.requestedUnits()),
                adapted.quotaDetails());
        assertEquals(List.of(
                new AuthorityProvenance("QuotaUsageAuthority", "PERIOD_USAGE", "usage-21")),
                adapted.provenance());
        assertFalse(adapted.stale());
    }
}
