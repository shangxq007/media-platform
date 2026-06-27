package com.example.platform.render.infrastructure.mlt;

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
 * MLT provider POC smoke test (dry-run).
 *
 * <p>Validates MLT provider metadata and eligibility rules without
 * requiring a real melt installation.</p>
 *
 * <p>MLT is POC status and NOT production-eligible.</p>
 */
class MLTProviderSmokeTest {

    @Test
    @DisplayName("MLT provider is POC status")
    void mltIsPocStatus() {
        ProviderMetadata mlt = createMLTMetadata();
        assertEquals(ProviderStatus.POC, mlt.status(), "MLT must be POC status");
        assertFalse(mlt.isProduction(), "MLT must not be production");
    }

    @Test
    @DisplayName("MLT provider has timeline capabilities")
    void mltHasTimelineCapabilities() {
        ProviderMetadata mlt = createMLTMetadata();
        assertTrue(mlt.declaredCapabilities().contains("timeline_render"),
                "MLT must declare timeline_render capability");
        assertTrue(mlt.declaredCapabilities().contains("multi_track"),
                "MLT must declare multi_track capability");
    }

    @Test
    @DisplayName("MLT is not eligible for production jobs")
    void mltNotEligibleForProduction() {
        ProviderMetadata mlt = createMLTMetadata();
        RenderJob productionJob = createProductionJob();
        assertFalse(ProviderEligibility.isEligible(mlt, productionJob),
                "MLT must not be eligible for production jobs");
    }

    @Test
    @DisplayName("MLT is eligible for manual jobs")
    void mltEligibleForManualJobs() {
        ProviderMetadata mlt = createMLTMetadata();
        RenderJob manualJob = createManualJob();
        assertTrue(ProviderEligibility.isEligible(mlt, manualJob),
                "MLT must be eligible for manual jobs");
    }

    private ProviderMetadata createMLTMetadata() {
        return new ProviderMetadata(
                "mlt", ProviderStatus.POC, "P1", ProviderType.TIMELINE,
                List.of("timeline_render", "multi_track", "transition", "audio_mix"),
                List.of("timeline_render", "multi_track", "transition", "audio_mix"),
                List.of(), List.of("caption_effects", "3d_render"),
                false, "melt", "MLT timeline render provider", List.of());
    }

    private RenderJob createProductionJob() {
        return new RenderJob("job-1", "video_export", "production", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", List.of("timeline_render"),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }

    private RenderJob createManualJob() {
        return new RenderJob("job-2", "video_export", "manual", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", List.of("timeline_render"),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }
}
