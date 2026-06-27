package com.example.platform.render.infrastructure.blender;

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
 * Blender provider SPIKE smoke test (dry-run).
 *
 * <p>Validates Blender provider metadata and eligibility rules without
 * requiring a real Blender installation.</p>
 *
 * <p>Blender is SPIKE status and NOT production-eligible.</p>
 */
class BlenderProviderSmokeTest {

    @Test
    @DisplayName("Blender provider is SPIKE status")
    void blenderIsSpikeStatus() {
        ProviderMetadata blender = createBlenderMetadata();
        assertEquals(ProviderStatus.SPIKE, blender.status(), "Blender must be SPIKE status");
        assertFalse(blender.isProduction(), "Blender must not be production");
    }

    @Test
    @DisplayName("Blender provider has 3D capabilities")
    void blenderHas3DCapabilities() {
        ProviderMetadata blender = createBlenderMetadata();
        assertTrue(blender.declaredCapabilities().contains("3d_render"),
                "Blender must declare 3d_render capability");
    }

    @Test
    @DisplayName("Blender is not eligible for production jobs")
    void blenderNotEligibleForProduction() {
        ProviderMetadata blender = createBlenderMetadata();
        RenderJob productionJob = createProductionJob();
        assertFalse(ProviderEligibility.isEligible(blender, productionJob),
                "Blender must not be eligible for production jobs");
    }

    @Test
    @DisplayName("Blender is eligible for manual jobs")
    void blenderEligibleForManualJobs() {
        ProviderMetadata blender = createBlenderMetadata();
        RenderJob manualJob = createManualJob();
        assertTrue(ProviderEligibility.isEligible(blender, manualJob),
                "Blender must be eligible for manual jobs");
    }

    private ProviderMetadata createBlenderMetadata() {
        return new ProviderMetadata(
                "blender", ProviderStatus.SPIKE, "P1", ProviderType.RENDER,
                List.of("3d_render"), List.of("3d_render"), List.of(),
                List.of("trim", "transcode", "timeline_render"),
                false, "blender", "Blender 3D render provider", List.of());
    }

    private RenderJob createProductionJob() {
        return new RenderJob("job-1", "video_export", "production", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", List.of("3d_render"),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }

    private RenderJob createManualJob() {
        return new RenderJob("job-2", "video_export", "manual", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", List.of("3d_render"),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }
}
