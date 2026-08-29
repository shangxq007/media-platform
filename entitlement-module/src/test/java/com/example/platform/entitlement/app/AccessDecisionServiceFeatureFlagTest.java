package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.entitlement.domain.*;
import com.example.platform.entitlement.domain.QuotaUsageQuery;
import com.example.platform.policy.featureflag.domain.*;
import com.example.platform.shared.commercial.CommercialDecisionReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

class AccessDecisionServiceFeatureFlagTest {

    private AccessDecisionService accessDecisionService;
    private EntitlementDecisionService entitlementDecisionService;
    private QuotaDecisionService quotaDecisionService;
    private AccessDecisionFeatureFlagService featureFlagService;

    @BeforeEach
    void setUp() {
        EntitlementPolicyService policyService = new EntitlementPolicyService(java.util.Optional.empty(), java.util.Optional.empty());
        entitlementDecisionService = new EntitlementDecisionService(
                policyService, mock(EntitlementService.class), java.util.Optional.empty(),
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
        featureFlagService = mock(AccessDecisionFeatureFlagService.class);
        accessDecisionService = new AccessDecisionService(
                entitlementDecisionService, quotaDecisionService, featureFlagService);
    }

    @Test
    void checkIncludesFeatureFlagDataInAllowDecision() {
        AccessCheckRequest request = new AccessCheckRequest(
                "tenant-enterprise", null, "user-1",
                "TENANT", "tenant-enterprise",
                "export", "export", null,
                "default_1080p", "default_1080p", null,
                "api", null, Map.of());

        FeatureFlagDecision ffDecision = new FeatureFlagDecision(
                "export.gpu.v2.enabled", true, "enabled",
                "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                "tenant-enterprise", null, "user-1", Instant.now(), Map.of());
        AccessDecisionFeatureFlagService.FeatureFlagAccessResult ffResult =
                new AccessDecisionFeatureFlagService.FeatureFlagAccessResult(
                        List.of(ffDecision), false, List.of());
        when(featureFlagService.evaluateForAccessDecision(request)).thenReturn(ffResult);

        AccessDecision decision = accessDecisionService.check(request);

        assertNotNull(decision);
        assertTrue(decision.allowed());
        assertNotNull(decision.matchedFeatureFlags());
        assertEquals(1, decision.matchedFeatureFlags().size());
        assertFalse(decision.disabledByFeatureFlag());
        assertTrue(decision.featureFlagReasons().isEmpty());
    }

    @Test
    void checkIncludesFeatureFlagDataInDenyDecision() {
        AccessCheckRequest request = new AccessCheckRequest(
                "tenant-1", null, "user-1",
                "TENANT", "tenant-1",
                "export", "export", null,
                "gpu_h264", "gpu_h264", null,
                "api", null, Map.of());

        FeatureFlagDecision ffDecision = new FeatureFlagDecision(
                "export.gpu.v2.enabled", false, "disabled",
                "FLAG_DISABLED", FeatureFlagProviderType.LOCAL, null,
                "tenant-1", null, "user-1", Instant.now(), Map.of());
        AccessDecisionFeatureFlagService.FeatureFlagAccessResult ffResult =
                new AccessDecisionFeatureFlagService.FeatureFlagAccessResult(
                        List.of(ffDecision), true,
                        List.of("Feature flag 'export.gpu.v2.enabled' is disabled (FLAG_DISABLED)"));
        when(featureFlagService.evaluateForAccessDecision(request)).thenReturn(ffResult);

        AccessDecision decision = accessDecisionService.check(request);

        assertNotNull(decision);
        assertFalse(decision.allowed());
        assertNotNull(decision.matchedFeatureFlags());
        assertTrue(decision.disabledByFeatureFlag());
        assertFalse(decision.featureFlagReasons().isEmpty());
    }

    @Test
    void checkBackwardCompatibilityWithLegacyConstructor() {
        AccessDecision decision = new AccessDecision(
                true, "ALLOW", "TIER", "Access granted",
                "ENTERPRISE", List.of("tier:ENTERPRISE"),
                null, null, null, null, null,
                List.of(), null, false);

        assertTrue(decision.allowed());
        assertNotNull(decision.matchedFeatureFlags());
        assertTrue(decision.matchedFeatureFlags().isEmpty());
        assertFalse(decision.disabledByFeatureFlag());
        assertTrue(decision.featureFlagReasons().isEmpty());
    }

    @Test
    void checkWithQuotaAndFeatureFlags() {
        AccessCheckRequest request = new AccessCheckRequest(
                "tenant-enterprise", null, "user-1",
                "TENANT", "tenant-enterprise",
                "render", "render", null,
                "default_1080p", "render.job.create", null,
                "api", 100L, Map.of());

        AccessDecisionFeatureFlagService.FeatureFlagAccessResult ffResult =
                new AccessDecisionFeatureFlagService.FeatureFlagAccessResult(
                        List.of(), false, List.of());
        when(featureFlagService.evaluateForAccessDecision(request)).thenReturn(ffResult);

        AccessDecision decision = accessDecisionService.check(request);

        assertNotNull(decision);
        assertTrue(decision.allowed());
        assertNotNull(decision.matchedFeatureFlags());
    }

    @Test
    void evaluateEntitlementStillWorks() {
        AccessCheckRequest request = new AccessCheckRequest(
                "tenant-1", null, "user-1",
                "TENANT", "tenant-1",
                "export", "export", null,
                "free_720p_watermarked", "free_720p_watermarked", null,
                "api", null, Map.of());

        AccessDecisionFeatureFlagService.FeatureFlagAccessResult ffResult =
                new AccessDecisionFeatureFlagService.FeatureFlagAccessResult(
                        List.of(), false, List.of());
        when(featureFlagService.evaluateForAccessDecision(request)).thenReturn(ffResult);

        EntitlementDecision decision = accessDecisionService.evaluateEntitlement(request);
        assertNotNull(decision);
        assertNotNull(decision.currentTier());
    }
}
