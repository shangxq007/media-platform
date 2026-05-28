package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.entitlement.domain.EntitlementPolicy;
import com.example.platform.entitlement.domain.ExportCapabilityPolicy;
import com.example.platform.entitlement.domain.ProviderAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntitlementPolicyServiceTenantTest {

    private EntitlementPolicyService service;

    @BeforeEach
    void setUp() {
        service = new EntitlementPolicyService(java.util.Optional.empty(), java.util.Optional.empty());
    }

    @Test
    void getTierReturnsFreeForUnknownTenant() {
        String tier = service.getTier("unknown-tenant-xyz");
        assertEquals("FREE", tier, "Unknown tenant should default to FREE tier");
    }

    @Test
    void getTierDoesNotContainTenant1Seed() {
        // After removing hardcoded seed data, "tenant-1" should NOT be pre-seeded in userTiers.
        // getTier returns FREE via getOrDefault, not because "tenant-1" was explicitly put.
        String tier = service.getTier("tenant-1");
        assertEquals("FREE", tier, "tenant-1 should get FREE via default, not via seed data");
        // The tier was never explicitly set via setTier, so decision source should not exist
        // (getTier does NOT write to decisionSources; only getPolicy does)
        assertNull(service.getDecisionSource("tenant-1"),
                "tenant-1 should not have a decision source since getPolicy was never called");
    }

    @Test
    void getPolicyReturnsDefaultForUnknownTenant() {
        EntitlementPolicy policy = service.getPolicy("brand-new-tenant");
        assertNotNull(policy);
        assertEquals("FREE", service.getTier("brand-new-tenant"));
    }

    @Test
    void setTierAndGetRoundTrip() {
        service.setTier("my-real-tenant", "PRO");
        assertEquals("PRO", service.getTier("my-real-tenant"));
    }

    @Test
    void multipleTenantsDoNotPolluteEachOther() {
        service.setTier("tenant-alpha", "PRO");
        service.setTier("tenant-beta", "TEAM");
        service.setTier("tenant-gamma", "FREE");

        assertEquals("PRO", service.getTier("tenant-alpha"));
        assertEquals("TEAM", service.getTier("tenant-beta"));
        assertEquals("FREE", service.getTier("tenant-gamma"));
    }

    @Test
    void unknownTenantsDoNotCreateSideEffects() {
        // Query many unknown tenants via getTier — none should create entries in decisionSources
        for (int i = 0; i < 100; i++) {
            service.getTier("unknown-" + i);
        }
        // getTier does NOT write to decisionSources, so none should exist
        for (int i = 0; i < 100; i++) {
            assertNull(service.getDecisionSource("unknown-" + i),
                    "Unknown tenant should not have a decision source from getTier");
        }
    }

    @Test
    void getExportCapabilitiesForUnknownTenant() {
        var caps = service.getExportCapabilities("nonexistent-tenant");
        assertNotNull(caps);
        assertFalse(caps.gpuExportAllowed());
    }

    @Test
    void getProviderAccessForUnknownTenant() {
        var access = service.getProviderAccess("nonexistent-tenant");
        assertNotNull(access);
        assertFalse(access.gpuAllowed());
    }
}
