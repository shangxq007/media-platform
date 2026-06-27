package com.example.platform.render.infrastructure.gstreamer;

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
 * GStreamer provider POC smoke test (dry-run).
 *
 * <p>Validates GStreamer provider metadata and eligibility rules without
 * requiring a real gst-launch-1.0 installation.</p>
 *
 * <p>GStreamer is HOLD status and NOT production-eligible.
 * Only for real-time streaming, low-latency pipeline, device capture.</p>
 */
class GStreamerProviderSmokeTest {

    @Test
    @DisplayName("GStreamer provider is HOLD status")
    void gstreamerIsHoldStatus() {
        ProviderMetadata gstreamer = createGStreamerMetadata();
        assertEquals(ProviderStatus.HOLD, gstreamer.status(), "GStreamer must be HOLD status");
        assertFalse(gstreamer.isProduction(), "GStreamer must not be production");
    }

    @Test
    @DisplayName("GStreamer provider has pipeline capabilities")
    void gstreamerHasPipelineCapabilities() {
        ProviderMetadata gstreamer = createGStreamerMetadata();
        assertTrue(gstreamer.declaredCapabilities().contains("realtime_pipeline"),
                "GStreamer must declare realtime_pipeline capability");
        assertTrue(gstreamer.declaredCapabilities().contains("streaming"),
                "GStreamer must declare streaming capability");
    }

    @Test
    @DisplayName("GStreamer is not eligible for production jobs")
    void gstreamerNotEligibleForProduction() {
        ProviderMetadata gstreamer = createGStreamerMetadata();
        RenderJob productionJob = createProductionJob();
        assertFalse(ProviderEligibility.isEligible(gstreamer, productionJob),
                "GStreamer must not be eligible for production jobs");
    }

    @Test
    @DisplayName("GStreamer is eligible for manual jobs with empty capabilities")
    void gstreamerEligibleForManualJobs() {
        ProviderMetadata gstreamer = createGStreamerMetadata();
        RenderJob manualJob = createManualJobEmptyCaps();
        assertTrue(ProviderEligibility.isEligible(gstreamer, manualJob),
                "GStreamer must be eligible for manual jobs");
    }

    @Test
    @DisplayName("GStreamer autoDispatch is false")
    void gstreamerAutoDispatchIsFalse() {
        ProviderMetadata gstreamer = createGStreamerMetadata();
        assertFalse(gstreamer.autoDispatch(), "GStreamer autoDispatch must be false");
    }

    private ProviderMetadata createGStreamerMetadata() {
        return new ProviderMetadata(
                "gstreamer", ProviderStatus.HOLD, "P2", ProviderType.MEDIA_PIPELINE,
                List.of("realtime_pipeline", "streaming"),
                List.of(), List.of("realtime_pipeline", "streaming"),
                List.of("timeline_render", "3d_render"),
                false, "gst-launch-1.0", "GStreamer pipeline provider", List.of());
    }

    private RenderJob createProductionJob() {
        return new RenderJob("job-1", "video_export", "production", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", List.of("streaming"),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }

    private RenderJob createManualJob() {
        return new RenderJob("job-2", "video_export", "manual", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", List.of("streaming"),
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
