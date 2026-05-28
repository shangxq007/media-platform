package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.entitlement.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

class AccessDecisionServiceTest {

    private AccessDecisionService accessDecisionService;
    private EntitlementDecisionService entitlementDecisionService;
    private QuotaDecisionService quotaDecisionService;

    @BeforeEach
    void setUp() {
        EntitlementPolicyService policyService = new EntitlementPolicyService(java.util.Optional.empty(), java.util.Optional.empty());
        // Explicitly set tiers for test fixtures (no longer seeded in production code)
        policyService.setTier("tenant-enterprise", "ENTERPRISE");
        policyService.setTier("tenant-1", "FREE");
        policyService.setTier("tenant-pro", "PRO");
        entitlementDecisionService = new EntitlementDecisionService(
                policyService, java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        QuotaPolicyService quotaPolicyService = new QuotaPolicyService();
        QuotaUsageService quotaUsageService = new QuotaUsageService(java.util.Optional.empty());
        quotaDecisionService = new QuotaDecisionService(quotaPolicyService, quotaUsageService);
        accessDecisionService = new AccessDecisionService(entitlementDecisionService, quotaDecisionService);
    }

    @Test
    void checkReturnsAllowForKnownTier() {
        AccessCheckRequest request = new AccessCheckRequest(
                "tenant-enterprise", null, "user-1",
                "TENANT", "tenant-enterprise",
                "export", "export", null,
                "default_1080p", "default_1080p", null,
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
                "gpu_h264", "gpu_h264", null,
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
                "default_1080p", "render.job.create", null,
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
                "default_1080p", "ai.model.premium", null,
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
                "free_720p_watermarked", "free_720p_watermarked", null,
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
                "team_4k", "team_4k", null,
                "api", null, Map.of());

        AccessDecision decision = accessDecisionService.check(request);
        assertNotNull(decision);
        assertFalse(decision.allowed());
        assertEquals("DEFAULT_DENY", decision.reasonCode());
    }
}
