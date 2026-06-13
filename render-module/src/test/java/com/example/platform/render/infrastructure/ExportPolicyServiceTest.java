package com.example.platform.render.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("native-media")
class ExportPolicyServiceTest {

    private ExportPolicyService policyService;

    @BeforeEach
    void setUp() {
        policyService = new ExportPolicyService();
    }

    @Test
    void freeTierCanOnlyExport720p() {
        ExportPolicyService.ExportPreset preset = policyService.getDefaultPreset("FREE");
        assertEquals("free_720p_watermarked", preset.name());
        assertTrue(preset.watermark());
    }

    @Test
    void freeCannotExport4k() {
        assertFalse(policyService.isPresetAvailable("team_4k", "FREE"));
        assertFalse(policyService.isPresetAvailable("enterprise_4k_ofx", "FREE"));
    }

    @Test
    void freeGetsWatermark() {
        assertTrue(policyService.requiresWatermark("FREE"));
    }

    @Test
    void proCanUseBasicOFX() {
        assertTrue(policyService.isPresetAvailable("pro_1080p", "PRO"));
        assertTrue(policyService.isPresetAvailable("default_1080p", "PRO"));
    }

    @Test
    void proDoesNotGetWatermark() {
        assertFalse(policyService.requiresWatermark("PRO"));
    }

    @Test
    void teamCanExport4k() {
        assertTrue(policyService.isPresetAvailable("team_4k", "TEAM"));
    }

    @Test
    void enterpriseCanUseAllStableProviders() {
        assertTrue(policyService.isPresetAvailable("enterprise_4k_ofx", "ENTERPRISE"));
        assertTrue(policyService.isPresetAvailable("team_4k", "ENTERPRISE"));
        assertTrue(policyService.isPresetAvailable("pro_1080p", "ENTERPRISE"));
    }

    @Test
    void experimentalCanUseExperimentalProviders() {
        assertTrue(policyService.isPresetAvailable("experimental_all_providers", "EXPERIMENTAL"));
        assertTrue(policyService.isExperimentalAllowed("EXPERIMENTAL"));
    }

    @Test
    void nonExperimentalCannotUseExperimental() {
        assertFalse(policyService.isPresetAvailable("experimental_all_providers", "FREE"));
        assertFalse(policyService.isPresetAvailable("experimental_all_providers", "PRO"));
        assertFalse(policyService.isPresetAvailable("experimental_all_providers", "ENTERPRISE"));
    }

    @Test
    void resolveProviderFreeUsesJavaCV() {
        assertEquals("javacv", policyService.resolveProvider("free_720p_watermarked", "FREE"));
    }

    @Test
    void resolveProviderProUsesJavaCV() {
        assertEquals("javacv", policyService.resolveProvider("pro_1080p", "PRO"));
    }

    @Test
    void resolveProviderEnterpriseUsesOFX() {
        assertEquals("ofx", policyService.resolveProvider("enterprise_4k_ofx", "ENTERPRISE"));
    }

    @Test
    void getAvailablePresetsReturnsCorrectList() {
        List<ExportPolicyService.ExportPreset> freePresets = policyService.getAvailablePresets("FREE");
        assertEquals(1, freePresets.size());
        assertEquals("free_720p_watermarked", freePresets.get(0).name());

        List<ExportPolicyService.ExportPreset> enterprisePresets = policyService.getAvailablePresets("ENTERPRISE");
        assertTrue(enterprisePresets.size() >= 3);
    }

    @Test
    void unknownTierDefaultsToFree() {
        ExportPolicyService.ExportTier tier = policyService.getTier("UNKNOWN");
        assertEquals("FREE", tier.name());
    }

    @Test
    void fallbackProviderWorksWhenPreferredUnavailable() {
        RenderProviderRegistry registry = new RenderProviderRegistry();
        JavaCVMediaProbeAdapter adapter = new JavaCVMediaProbeAdapter();
        MediaProbeService probeService = new MediaProbeService(adapter);
        JavaCVRenderProvider javacv = new JavaCVRenderProvider(new JavaCVRenderService(probeService), new JavaCVTranscodeService(probeService),
                new com.example.platform.render.domain.timeline.TimelineScriptParser());
        registry.register("javacv", javacv, javacv.getCapability());

        RenderProviderSelectionPolicy selectionPolicy = new RenderProviderSelectionPolicy(
                registry, new com.example.platform.render.infrastructure.effects.EffectProviderRouter(
                        new EffectMappingService()));
        RenderProviderFallbackPolicy fallbackPolicy = new RenderProviderFallbackPolicy(registry, selectionPolicy);

        // Should fall back to available provider
        RenderProvider provider = fallbackPolicy.resolve("default_1080p", List.of());
        assertInstanceOf(JavaCVRenderProvider.class, provider);
    }
}
