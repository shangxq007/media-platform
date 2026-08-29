package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.entitlement.domain.AccessDecision;
import com.example.platform.shared.commercial.CommercialAdmissionRequest;
import com.example.platform.shared.commercial.CommercialDecisionReason;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.shared.commercial.QuotaDecision;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommercialAdmissionServiceTest {
    private static final PrincipalRef PRINCIPAL = PrincipalRef.tenantScoped(
            "tenant-1", PrincipalType.ORGANIZATION, "tenant-1");
    private static final Instant START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-09-01T00:00:00Z");

    private EntitlementService entitlements;
    private QuotaDecisionService quota;
    private CommercialAdmissionService service;

    @BeforeEach
    void setUp() {
        entitlements = mock(EntitlementService.class);
        quota = mock(QuotaDecisionService.class);
        service = new CommercialAdmissionService(entitlements, quota);
    }

    @Test
    void explicitGrantAndCanonicalQuotaDecisionAreBothRequired() {
        when(entitlements.checkFeature(PRINCIPAL, "render.job.create")).thenReturn(
                access(true, "grant-1", "explicit-grant"));
        when(quota.evaluate(PRINCIPAL, "render.job.create", START, END, 1,
                "trace-1", START)).thenReturn(quota(true));

        var decision = service.decide(request());

        assertTrue(decision.allowed());
        assertEquals(CommercialDecisionReason.ALLOWED, decision.reason());
        verify(entitlements).checkFeature(PRINCIPAL, "render.job.create");
        verify(quota).evaluate(PRINCIPAL, "render.job.create", START, END, 1, "trace-1", START);
    }

    @Test
    void commercialTierMetadataCannotGrantWithoutAnExplicitEntitlement() {
        when(entitlements.checkFeature(PRINCIPAL, "render.job.create")).thenReturn(
                access(false, null, "no-grant"));

        var decision = service.decide(request());

        assertFalse(decision.allowed());
        assertEquals(CommercialDecisionReason.NOT_ENTITLED, decision.reason());
    }

    @Test
    void authorityFailureFailsClosed() {
        when(entitlements.checkFeature(PRINCIPAL, "render.job.create"))
                .thenThrow(new IllegalStateException("persistence unavailable"));

        var decision = service.decide(request());

        assertFalse(decision.allowed());
        assertEquals(CommercialDecisionReason.POLICY_DENIED, decision.reason());
    }

    private static CommercialAdmissionRequest request() {
        return new CommercialAdmissionRequest(PRINCIPAL, "render.submit",
                "render.job.create", "render.job.create", 1,
                START, END, "trace-1", START);
    }

    private static AccessDecision access(boolean allowed, String grantId, String reason) {
        return new AccessDecision(allowed, allowed ? "ALLOW" : "DENY", reason,
                allowed ? "granted" : "denied", null, List.of(), grantId,
                null, null, null, null, List.of(), null, false);
    }

    private static QuotaDecision quota(boolean allowed) {
        return new QuotaDecision(PRINCIPAL, "render.job.create", 1, 100, 0,
                allowed, allowed ? CommercialDecisionReason.ALLOWED : CommercialDecisionReason.QUOTA_EXCEEDED,
                List.of(), "quota-v1", "trace-1", START);
    }
}
