package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.entitlement.domain.*;
import com.example.platform.entitlement.domain.QuotaUsageQuery;
import com.example.platform.shared.commercial.CommercialDecisionReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessDecisionServiceTest {

    private AccessDecisionService accessDecisionService;
    private EntitlementDecisionService entitlementDecisionService;
    private QuotaDecisionService quotaDecisionService;

    @BeforeEach
    void setUp() {
        EntitlementPolicyService policyService = new EntitlementPolicyService(java.util.Optional.empty());
        // Explicitly set tiers for test fixtures (no longer seeded in production code)
        policyService.setTier("tenant-enterprise", "ENTERPRISE");
        policyService.setTier("tenant-1", "FREE");
        policyService.setTier("tenant-pro", "PRO");
        EntitlementService entitlements = mock(EntitlementService.class);
        when(entitlements.listGrants(any())).thenReturn(List.of(
                grant("export.preset.default_1080p"),
                grant("render.job.create"),
                grant("ai.model.premium")));
        entitlementDecisionService = new EntitlementDecisionService(
                policyService, entitlements, java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.Optional.empty());
        QuotaPolicyService quotaPolicyService = new QuotaPolicyService();
        QuotaUsageAuthority quotaUsageAuthority = mock(QuotaUsageAuthority.class);
        when(quotaUsageAuthority.decide(any(QuotaUsageQuery.class))).thenAnswer(invocation -> {
            QuotaUsageQuery query = invocation.getArgument(0);
            boolean allowed = query.requestedUnits() <= query.limitUnits();
            return new com.example.platform.shared.commercial.QuotaDecision(
                    query.principal(), query.quotaKey(), query.requestedUnits(),
                    query.limitUnits(), 0, allowed,
                    allowed ? CommercialDecisionReason.ALLOWED : CommercialDecisionReason.QUOTA_EXCEEDED,
                    java.util.List.of(), "quota-usage-v1", query.traceId(), query.decidedAt());
        });
        quotaDecisionService = new QuotaDecisionService(quotaPolicyService, quotaUsageAuthority);
        accessDecisionService = new AccessDecisionService(entitlementDecisionService, quotaDecisionService);
    }

    @Test
    void checkReturnsAllowForKnownTier() {
        AccessCheckRequest request = new AccessCheckRequest(
                "tenant-enterprise", null, "user-1",
                "TENANT", "tenant-enterprise",
                "export", "export", null,
                "export.preset.default_1080p", "default_1080p", null,
                "api", null, Map.of());

        AccessDecision decision = accessDecisionService.check(request);
        assertNotNull(decision);
        assertTrue(decision.allowed());
        assertEquals("ENTERPRISE", decision.currentTier());
    }

    @Test
    void checkReturnsDenyForFreeTierWithGpuPreset() {
        AccessCheckRequest request = new AccessCheckRequest(
                "tenant-1", null, "user-1",
                "TENANT", "tenant-1",
                "export", "export", null,
                "export.preset.gpu_h264", "gpu_h264", null,
                "api", null, Map.of());

        AccessDecision decision = accessDecisionService.check(request);
        assertNotNull(decision);
        assertFalse(decision.allowed());
        assertNotNull(decision.upgradeOptions());
        assertFalse(decision.upgradeOptions().isEmpty());
    }

    @Test
    void checkWithQuotaRequest() {
        AccessCheckRequest request = new AccessCheckRequest(
                "tenant-enterprise", null, "user-1",
                "TENANT", "tenant-enterprise",
                "render", "render", null,
                "render.job.create", null, null,
                "api", 100L, Map.of());

        AccessDecision decision = accessDecisionService.check(request);
        assertNotNull(decision);
        assertTrue(decision.allowed());
    }

    @Test
    void checkDeniesQuotaExceeded() {
        AccessCheckRequest request = new AccessCheckRequest(
                "tenant-enterprise", null, "user-1",
                "TENANT", "tenant-enterprise",
                "render", "render", null,
                "ai.model.premium", null, null,
                "api", 999999L, Map.of());

        AccessDecision decision = accessDecisionService.check(request);
        assertNotNull(decision);
        assertFalse(decision.allowed());
    }

    @Test
    void evaluateEntitlementReturnsDecision() {
        AccessCheckRequest request = new AccessCheckRequest(
                "tenant-1", null, "user-1",
                "TENANT", "tenant-1",
                "export", "export", null,
                "export.preset.free_720p_watermarked", "free_720p_watermarked", null,
                "api", null, Map.of());

        EntitlementDecision decision = accessDecisionService.evaluateEntitlement(request);
        assertNotNull(decision);
        assertNotNull(decision.currentTier());
    }

    @Test
    void checkReturnsDenyWithDefaultDeny() {
        AccessCheckRequest request = new AccessCheckRequest(
                "tenant-1", null, "user-1",
                "TENANT", "tenant-1",
                "access", "feature", null,
                "export.preset.team_4k", "team_4k", null,
                "api", null, Map.of());

        AccessDecision decision = accessDecisionService.check(request);
        assertNotNull(decision);
        assertFalse(decision.allowed());
        assertEquals("DEFAULT_DENY", decision.reasonCode());
    }

    private static EntitlementGrantView grant(String key) {
        return new EntitlementGrantView("grant-" + key, null, key, null,
                "TEST", "test", "ACTIVE", null, null, 1, false);
    }
}
