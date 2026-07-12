package com.example.platform.render.infrastructure.gpac;

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
 * GPAC packaging provider POC smoke test (dry-run).
 *
 * <p>Validates GPAC provider metadata, eligibility rules, and safety
 * constraints without requiring a real MP4Box installation.</p>
 *
 * <p>GPAC is POC status and NOT production-eligible:
 * <ul>
 *   <li>autoDispatch = true (for packaging jobs only)</li>
 *   <li>Not eligible for production jobs without explicit allow</li>
 *   <li>Packaging-oriented capability only</li>
 *   <li>No full timeline render replacement</li>
 * </ul>
 */
class GPACProviderSmokeTest {

    // ── Provider Metadata Validation ──

    @Test
    @DisplayName("GPAC provider is POC status")
    void gpacIsPocStatus() {
        ProviderMetadata gpac = createGPACMetadata();
        assertEquals(ProviderStatus.POC, gpac.status(), "GPAC must be POC status");
        assertTrue(gpac.isPoc(), "GPAC must be POC");
        assertFalse(gpac.isProduction(), "GPAC must not be production");
    }

    @Test
    @DisplayName("GPAC provider has packaging capabilities")
    void gpacHasPackagingCapabilities() {
        ProviderMetadata gpac = createGPACMetadata();
        assertTrue(gpac.declaredCapabilities().contains("package_hls"),
                "GPAC must declare package_hls capability");
        assertTrue(gpac.declaredCapabilities().contains("package_dash"),
                "GPAC must declare package_dash capability");
        assertTrue(gpac.declaredCapabilities().contains("package_cmaf"),
                "GPAC must declare package_cmaf capability");
    }

    @Test
    @DisplayName("GPAC provider does not handle video editing capabilities")
    void gpacDoesNotHandleVideoEditing() {
        ProviderMetadata gpac = createGPACMetadata();
        assertFalse(gpac.canHandleCapability("trim"),
                "GPAC must not handle trim");
        assertFalse(gpac.canHandleCapability("transcode"),
                "GPAC must not handle transcode");
        assertFalse(gpac.canHandleCapability("timeline_render"),
                "GPAC must not handle timeline_render");
        assertFalse(gpac.canHandleCapability("3d_render"),
                "GPAC must not handle 3d_render");
    }

    // ── Eligibility Validation ──

    @Test
    @DisplayName("GPAC is not eligible for production jobs")
    void gpacNotEligibleForProduction() {
        ProviderMetadata gpac = createGPACMetadata();
        RenderJob productionJob = createProductionJob();

        assertFalse(ProviderEligibility.isEligible(gpac, productionJob),
                "GPAC must not be eligible for production jobs");
    }

    @Test
    @DisplayName("GPAC is eligible for manual jobs")
    void gpacEligibleForManualJobs() {
        ProviderMetadata gpac = createGPACMetadata();
        RenderJob manualJob = createManualJob();

        assertTrue(ProviderEligibility.isEligible(gpac, manualJob),
                "GPAC must be eligible for manual jobs");
    }

    @Test
    @DisplayName("GPAC is eligible for experiment jobs")
    void gpacEligibleForExperimentJobs() {
        ProviderMetadata gpac = createGPACMetadata();
        RenderJob experimentJob = createExperimentJob();

        assertTrue(ProviderEligibility.isEligible(gpac, experimentJob),
                "GPAC must be eligible for experiment jobs");
    }

    // ── Safety Constraints ──

    @Test
    @DisplayName("GPAC metadata does not expose sensitive fields")
    void gpacMetadataSafe() {
        ProviderMetadata gpac = createGPACMetadata();
        String metaString = gpac.toString();

        assertFalse(metaString.contains("secret"), "Metadata must not contain 'secret'");
        assertFalse(metaString.contains("password"), "Metadata must not contain 'password'");
        assertFalse(metaString.contains("token"), "Metadata must not contain 'token'");
        assertFalse(metaString.contains("signedUrl"), "Metadata must not contain 'signedUrl'");
    }

    // ── Helper Methods ──

    private ProviderMetadata createGPACMetadata() {
        return new ProviderMetadata(
                "gpac",
                ProviderStatus.POC,
                "P1",
                ProviderType.PACKAGING,
                List.of("package_hls", "package_dash", "package_cmaf"),
                List.of("package_hls", "package_dash", "package_cmaf"),
                List.of(),
                List.of("trim", "transcode", "3d_render"),
                true,  // autoDispatch = true for packaging
                "MP4Box",
                "GPAC packaging provider for DASH/HLS/CMAF streaming delivery",
                List.of("Not a general render provider - only for packaging/streaming delivery")
        );
    }

    private RenderJob createProductionJob() {
        return new RenderJob("job-1", "video_export", "production", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", List.of("package_hls"),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }

    private RenderJob createManualJob() {
        return new RenderJob("job-2", "video_export", "manual", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", List.of("package_hls"),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }

    private RenderJob createExperimentJob() {
        return new RenderJob("job-3", "video_export", "experiment", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", List.of("package_hls"),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }
}
