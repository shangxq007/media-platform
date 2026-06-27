package com.example.platform.render.infrastructure.natron;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.infrastructure.ProviderEligibility;
import com.example.platform.render.infrastructure.ProviderMetadata;
import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.ProviderType;
import com.example.platform.render.infrastructure.RenderJob;
import com.example.platform.render.infrastructure.RenderConstraints;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Natron provider SPIKE smoke test (dry-run).
 *
 * <p>Validates Natron provider metadata and eligibility rules without
 * requiring a real Natron installation.</p>
 *
 * <p>Natron is HOLD status and NOT production-eligible.
 * Does not participate in auto-routing.</p>
 */
class NatronProviderSmokeTest {

    @Test
    @DisplayName("Natron provider is HOLD status")
    void natronIsHoldStatus() {
        ProviderMetadata natron = createNatronMetadata();
        assertEquals(ProviderStatus.HOLD, natron.status(), "Natron must be HOLD status");
        assertFalse(natron.isProduction(), "Natron must not be production");
    }

    @Test
    @DisplayName("Natron provider has VFX capabilities")
    void natronHasVfxCapabilities() {
        ProviderMetadata natron = createNatronMetadata();
        assertTrue(natron.declaredCapabilities().contains("node_effects"),
                "Natron must declare node_effects capability");
        assertTrue(natron.declaredCapabilities().contains("vfx_composite"),
                "Natron must declare vfx_composite capability");
    }

    @Test
    @DisplayName("Natron is not eligible for production jobs")
    void natronNotEligibleForProduction() {
        ProviderMetadata natron = createNatronMetadata();
        RenderJob productionJob = createProductionJob();
        assertFalse(ProviderEligibility.isEligible(natron, productionJob),
                "Natron must not be eligible for production jobs");
    }

    @Test
    @DisplayName("Natron is eligible for manual jobs with empty capabilities")
    void natronEligibleForManualJobs() {
        ProviderMetadata natron = createNatronMetadata();
        RenderJob manualJob = createManualJobEmptyCaps();
        assertTrue(ProviderEligibility.isEligible(natron, manualJob),
                "Natron must be eligible for manual jobs");
    }

    @Test
    @DisplayName("Natron autoDispatch is false")
    void natronAutoDispatchIsFalse() {
        ProviderMetadata natron = createNatronMetadata();
        assertFalse(natron.autoDispatch(), "Natron autoDispatch must be false");
    }

    private ProviderMetadata createNatronMetadata() {
        return new ProviderMetadata(
                "natron", ProviderStatus.HOLD, "P3", ProviderType.RENDER,
                List.of("node_effects", "vfx_composite"),
                List.of(), List.of("node_effects", "vfx_composite"),
                List.of("trim", "transcode", "timeline_render"),
                false, "natron", "Natron compositing provider", List.of());
    }

    private RenderJob createProductionJob() {
        return new RenderJob("job-1", "video_export", "production", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", List.of("vfx_composite"),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }

    private RenderJob createManualJob() {
        return new RenderJob("job-2", "video_export", "manual", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", List.of("vfx_composite"),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }

    private RenderJob createManualJobEmptyCaps() {
        return new RenderJob("job-2", "video_export", "manual", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", List.of(),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }
}
