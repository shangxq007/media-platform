package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.*;
import com.example.platform.shared.entitlement.EntitlementPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntitlementPolicyServiceTest {

    private EntitlementPolicyService service;

    @BeforeEach
    void setUp() {
        service = new EntitlementPolicyService(java.util.Optional.empty(), java.util.Optional.empty());
    }

    @Test
    void shouldReturnFreeTierByDefault() {
        String tier = service.getTier("unknown-tenant");
        assertEquals("FREE", tier);
    }

    @Test
    void shouldSetAndGetTier() {
        service.setTier("tenant-1", "PRO");
        assertEquals("PRO", service.getTier("tenant-1"));
    }

    @Test
    void shouldReturnEntitlementPolicyForTier() {
        service.setTier("tenant-1", "FREE");
        EntitlementPolicy policy = service.getPolicy("tenant-1");
        assertNotNull(policy);
        assertEquals("FREE", policy.tier());
        assertEquals(1280, policy.maxResolutionWidth());
        assertEquals(720, policy.maxResolutionHeight());
        assertTrue(policy.watermark());
        assertFalse(policy.gpuAllowed());
    }

    @Test
    void shouldReturnExportCapabilities() {
        service.setTier("tenant-1", "PRO");
        ExportCapabilityPolicy caps = service.getExportCapabilities("tenant-1");
        assertNotNull(caps);
        assertEquals("PRO", caps.tier());
        assertTrue(caps.isFormatAllowed("mp4"));
        assertTrue(caps.isFormatAllowed("webm"));
        assertTrue(caps.isFormatAllowed("mov"));
        assertTrue(caps.isPresetAllowed("default_1080p"));
        assertFalse(caps.isPresetAllowed("team_4k"));
    }

    @Test
    void shouldReturnProviderAccess() {
        service.setTier("tenant-1", "TEAM");
        ProviderAccessPolicy access = service.getProviderAccess("tenant-1");
        assertNotNull(access);
        assertTrue(access.isProviderAllowed("javacv"));
        assertTrue(access.isProviderAllowed("ofx"));
        assertTrue(access.isProviderAllowed("remote-javacv"));
        assertTrue(access.gpuAllowed());
    }

    @Test
    void shouldValidateFreeTierExport() {
        service.setTier("tenant-1", "FREE");
        EntitlementPort.ExportValidationResult result = service.validateExport(
                "tenant-1", "user-1", "free_720p_watermarked", "mp4", 60);
        assertNotNull(result);
        assertTrue(result.allowed());
        assertEquals("FREE", result.currentTier());
        assertEquals("CLIENT", result.recommendedRenderLocation());
        assertTrue(result.clientExportSupported());
    }

    @Test
    void shouldDeny4kForFreeTier() {
        service.setTier("tenant-1", "FREE");
        EntitlementPort.ExportValidationResult result = service.validateExport(
                "tenant-1", "user-1", "team_4k", "mp4", 60);
        assertNotNull(result);
        assertFalse(result.allowed());
        assertNotNull(result.recommendedPreset());
        assertFalse(result.upgradeOptions().isEmpty());
    }

    @Test
    void shouldDenyGpuForFreeTier() {
        service.setTier("tenant-1", "FREE");
        EntitlementPort.ExportValidationResult result = service.validateExport(
                "tenant-1", "user-1", "gpu_h264", "mp4", 60);
        assertNotNull(result);
        assertFalse(result.allowed());
    }

    @Test
    void shouldAllowGpuForTeamTier() {
        service.setTier("tenant-1", "TEAM");
        EntitlementPort.ExportValidationResult result = service.validateExport(
                "tenant-1", "user-1", "gpu_h264", "mp4", 60);
        assertNotNull(result);
        assertTrue(result.allowed());
    }

    @Test
    void shouldCheckFeatureFlags() {
        assertTrue(service.isFeatureEnabled("FREE", "watermark"));
        assertFalse(service.isFeatureEnabled("FREE", "gpu-render"));
        assertTrue(service.isFeatureEnabled("TEAM", "gpu-render"));
        assertTrue(service.isFeatureEnabled("ENTERPRISE", "priority-queue"));
    }

    @Test
    void shouldReturnUpgradeOptions() {
        service.setTier("tenant-1", "FREE");
        EntitlementPort.ExportValidationResult result = service.validateExport(
                "tenant-1", "user-1", "team_4k", "mp4", 60);
        assertFalse(result.upgradeOptions().isEmpty());
        assertTrue(result.upgradeOptions().stream().anyMatch(o -> o.contains("PRO")));
    }

    @Test
    void shouldRecommendPresetWhenDenied() {
        service.setTier("tenant-1", "FREE");
        EntitlementPort.ExportValidationResult result = service.validateExport(
                "tenant-1", "user-1", "pro_1080p", "mp4", 60);
        assertFalse(result.allowed());
        assertNotNull(result.recommendedPreset());
        assertNotEquals("pro_1080p", result.recommendedPreset());
    }
}
