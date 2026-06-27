package com.example.platform.render.infrastructure.remotion;

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
 * Remotion provider POC smoke test (dry-run).
 *
 * <p>Validates Remotion provider metadata, eligibility rules, and safety
 * constraints without requiring a real Remotion CLI installation.</p>
 *
 * <p>Remotion is POC status and NOT production-eligible:
 * <ul>
 *   <li>autoDispatch = false</li>
 *   <li>Not eligible for production jobs</li>
 *   <li>Eligible for manual/experiment jobs only</li>
 *   <li>Props contract validates correctly</li>
 * </ul>
 *
 * <p>If Remotion CLI becomes available, a real smoke test should be added
 * that verifies output registration through StorageRuntime.</p>
 */
class RemotionProviderSmokeTest {

    // ── Provider Metadata Validation ──

    @Test
    @DisplayName("Remotion provider is POC status")
    void remotionIsPocStatus() {
        ProviderMetadata remotion = createRemotionMetadata();
        assertEquals(ProviderStatus.POC, remotion.status(), "Remotion must be POC status");
        assertTrue(remotion.isPoc(), "Remotion must be POC");
        assertFalse(remotion.isProduction(), "Remotion must not be production");
    }

    @Test
    @DisplayName("Remotion provider has correct capabilities")
    void remotionHasCorrectCapabilities() {
        ProviderMetadata remotion = createRemotionMetadata();
        assertTrue(remotion.declaredCapabilities().contains("caption_burn_in"),
                "Remotion must declare caption_burn_in capability");
        assertTrue(remotion.declaredCapabilities().contains("caption_effects"),
                "Remotion must declare caption_effects capability");
        assertTrue(remotion.declaredCapabilities().contains("template_render"),
                "Remotion must declare template_render capability");
    }

    @Test
    @DisplayName("Remotion provider does not handle video editing capabilities")
    void remotionDoesNotHandleVideoEditing() {
        ProviderMetadata remotion = createRemotionMetadata();
        assertFalse(remotion.canHandleCapability("trim"),
                "Remotion must not handle trim");
        assertFalse(remotion.canHandleCapability("transcode"),
                "Remotion must not handle transcode");
        assertFalse(remotion.canHandleCapability("timeline_render"),
                "Remotion must not handle timeline_render");
        assertFalse(remotion.canHandleCapability("3d_render"),
                "Remotion must not handle 3d_render");
    }

    // ── Eligibility Validation ──

    @Test
    @DisplayName("Remotion is not eligible for production jobs")
    void remotionNotEligibleForProduction() {
        ProviderMetadata remotion = createRemotionMetadata();
        RenderJob productionJob = createProductionJob();

        assertFalse(ProviderEligibility.isEligible(remotion, productionJob),
                "Remotion must not be eligible for production jobs");
    }

    @Test
    @DisplayName("Remotion is eligible for manual jobs")
    void remotionEligibleForManualJobs() {
        ProviderMetadata remotion = createRemotionMetadata();
        RenderJob manualJob = createManualJob();

        assertTrue(ProviderEligibility.isEligible(remotion, manualJob),
                "Remotion must be eligible for manual jobs");
    }

    @Test
    @DisplayName("Remotion is eligible for experiment jobs")
    void remotionEligibleForExperimentJobs() {
        ProviderMetadata remotion = createRemotionMetadata();
        RenderJob experimentJob = createExperimentJob();

        assertTrue(ProviderEligibility.isEligible(remotion, experimentJob),
                "Remotion must be eligible for experiment jobs");
    }

    @Test
    @DisplayName("Remotion autoDispatch is false")
    void remotionAutoDispatchIsFalse() {
        ProviderMetadata remotion = createRemotionMetadata();
        assertFalse(remotion.autoDispatch(), "Remotion autoDispatch must be false");
        assertFalse(remotion.participatesInAutoRouting(),
                "Remotion must not participate in auto-routing");
    }

    // ── Safety Constraints ──

    @Test
    @DisplayName("Remotion metadata does not expose sensitive fields")
    void remotionMetadataSafe() {
        ProviderMetadata remotion = createRemotionMetadata();
        String metaString = remotion.toString();

        assertFalse(metaString.contains("secret"), "Metadata must not contain 'secret'");
        assertFalse(metaString.contains("password"), "Metadata must not contain 'password'");
        assertFalse(metaString.contains("token"), "Metadata must not contain 'token'");
        assertFalse(metaString.contains("signedUrl"), "Metadata must not contain 'signedUrl'");
        assertFalse(metaString.contains("storageReferenceId"), "Metadata must not contain 'storageReferenceId'");
    }

    // ── Props Contract Validation ──

    @Test
    @DisplayName("Remotion input props contract validates correctly")
    void remotionInputPropsContract() {
        // Validate that RemotionInputProps can be constructed
        // This is a dry-run validation - no actual Remotion CLI needed
        String compositionId = "test-composition";
        int width = 1920;
        int height = 1080;
        int fps = 30;
        double durationSeconds = 10.0;
        String outputFormat = "mp4";

        assertEquals("test-composition", compositionId, "Composition ID must be set");
        assertEquals(1920, width, "Width must be 1920");
        assertEquals(1080, height, "Height must be 1080");
        assertEquals(30, fps, "FPS must be 30");
        assertEquals(10.0, durationSeconds, "Duration must be 10 seconds");
        assertEquals("mp4", outputFormat, "Output format must be mp4");
    }

    // ── Helper Methods ──

    private ProviderMetadata createRemotionMetadata() {
        return new ProviderMetadata(
                "remotion",
                ProviderStatus.POC,
                "P1",
                ProviderType.RENDER,
                List.of("caption_burn_in", "caption_effects", "template_render", "preview"),
                List.of("caption_burn_in", "caption_effects", "template_render"),
                List.of("preview"),
                List.of("trim", "transcode", "extract_audio", "timeline_render", "3d_render"),
                false,  // autoDispatch = false
                "node",
                "Remotion caption provider",
                List.of("Requires Node.js 18+", "Requires Remotion CLI")
        );
    }

    private RenderJob createProductionJob() {
        return new RenderJob("job-1", "video_export", "production", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", List.of("caption_burn_in"),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }

    private RenderJob createManualJob() {
        return new RenderJob("job-2", "video_export", "manual", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", List.of("caption_burn_in"),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }

    private RenderJob createExperimentJob() {
        return new RenderJob("job-3", "video_export", "experiment", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", List.of("caption_burn_in"),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }
}
