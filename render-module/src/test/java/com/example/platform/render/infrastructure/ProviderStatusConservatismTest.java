package com.example.platform.render.infrastructure;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests verifying that provider status is conservative and future providers
 * are not production-eligible by default.
 *
 * <p>These tests ensure the provider registry maintains safe defaults:
 * <ul>
 *   <li>FFmpeg is the only production baseline</li>
 *   <li>Future providers (Remotion, MLT, Blender, etc.) are not auto-dispatch eligible</li>
 *   <li>Provider details are not exposed in public APIs</li>
 * </ul>
 */
class ProviderStatusConservatismTest {

    // ── Provider Metadata Factories ──

    private ProviderMetadata ffmpegMetadata() {
        return new ProviderMetadata(
                "ffmpeg", ProviderStatus.PRODUCTION, "P0", ProviderType.RENDER,
                List.of("trim", "transcode", "mux", "demux", "extract_audio",
                        "thumbnail", "caption_burn_in", "output_normalize"),
                List.of("trim", "transcode", "mux", "demux", "extract_audio",
                        "thumbnail", "output_normalize"),
                List.of("caption_burn_in"),
                List.of("caption_effects", "template_render", "timeline_render", "3d_render"),
                true, "ffmpeg", "FFmpeg baseline render provider", List.of());
    }

    private ProviderMetadata remotionMetadata() {
        return new ProviderMetadata(
                "remotion", ProviderStatus.POC, "P1", ProviderType.RENDER,
                List.of("caption_burn_in", "caption_effects", "template_render", "preview"),
                List.of("caption_burn_in", "caption_effects", "template_render"),
                List.of("preview"),
                List.of("trim", "transcode", "extract_audio", "timeline_render", "3d_render"),
                false, "node", "Remotion caption provider", List.of());
    }

    private ProviderMetadata mltMetadata() {
        return new ProviderMetadata(
                "mlt", ProviderStatus.POC, "P1", ProviderType.TIMELINE,
                List.of("timeline_render", "multi_track", "transition", "audio_mix"),
                List.of("timeline_render", "multi_track", "transition", "audio_mix"),
                List.of(),
                List.of("caption_effects", "3d_render"),
                false, "melt", "MLT timeline render provider", List.of());
    }

    private ProviderMetadata blenderMetadata() {
        return new ProviderMetadata(
                "blender", ProviderStatus.POC, "P1", ProviderType.RENDER,
                List.of("3d_render"),
                List.of("3d_render"),
                List.of(),
                List.of("trim", "transcode", "timeline_render"),
                false, "blender", "Blender 3D render provider", List.of());
    }

    private ProviderMetadata natronMetadata() {
        return new ProviderMetadata(
                "natron", ProviderStatus.HOLD, "P3", ProviderType.RENDER,
                List.of("node_effects", "vfx_composite"),
                List.of(),
                List.of("node_effects", "vfx_composite"),
                List.of("trim", "transcode", "timeline_render"),
                false, "natron", "Natron compositing provider", List.of());
    }

    private ProviderMetadata gstreamerMetadata() {
        return new ProviderMetadata(
                "gstreamer", ProviderStatus.HOLD, "P2", ProviderType.MEDIA_PIPELINE,
                List.of("realtime_pipeline", "streaming"),
                List.of(),
                List.of("realtime_pipeline", "streaming"),
                List.of("timeline_render", "3d_render"),
                false, "gst-launch-1.0", "GStreamer pipeline provider", List.of());
    }

    private ProviderMetadata gpacMetadata() {
        return new ProviderMetadata(
                "gpac", ProviderStatus.POC, "P1", ProviderType.PACKAGING,
                List.of("package_hls", "package_dash", "package_cmaf"),
                List.of("package_hls", "package_dash", "package_cmaf"),
                List.of(),
                List.of("trim", "transcode", "3d_render"),
                false, "MP4Box", "GPAC packaging provider", List.of());
    }

    private RenderJob productionJob(List<String> requiredCaps) {
        return new RenderJob("job-1", "video_export", "production", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", requiredCaps,
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }

    // ── FFmpeg is the only production baseline ──

    @Test
    void ffmpegIsProductionStatus() {
        assertEquals(ProviderStatus.PRODUCTION, ffmpegMetadata().status());
        assertTrue(ffmpegMetadata().isProduction());
        assertTrue(ffmpegMetadata().autoDispatch());
    }

    @Test
    void ffmpegIsEligibleForProductionJob() {
        assertTrue(ProviderEligibility.isEligible(ffmpegMetadata(), productionJob(List.of("trim"))));
        assertTrue(ProviderEligibility.isEligible(ffmpegMetadata(), productionJob(List.of("transcode"))));
        assertTrue(ProviderEligibility.isEligible(ffmpegMetadata(), productionJob(List.of("thumbnail"))));
    }

    @Test
    void ffmpegIsLowestScoreForProductionJob() {
        int ffmpegScore = ProviderEligibility.scoreProvider(ffmpegMetadata(), productionJob(List.of("trim")));
        int remotionScore = ProviderEligibility.scoreProvider(remotionMetadata(), productionJob(List.of("caption_burn_in")));
        int mltScore = ProviderEligibility.scoreProvider(mltMetadata(), productionJob(List.of("timeline_render")));

        assertTrue(ffmpegScore < remotionScore, "FFmpeg must score lower (better) than Remotion");
        assertTrue(ffmpegScore < mltScore, "FFmpeg must score lower (better) than MLT");
    }

    // ── Future providers are not production-eligible ──

    @Test
    void remotionIsNotProductionEligible() {
        ProviderMetadata remotion = remotionMetadata();
        assertNotEquals(ProviderStatus.PRODUCTION, remotion.status());
        assertFalse(remotion.autoDispatch());
        assertFalse(ProviderEligibility.isEligible(remotion, productionJob(List.of("caption_burn_in"))));
    }

    @Test
    void mltIsNotProductionEligible() {
        ProviderMetadata mlt = mltMetadata();
        assertNotEquals(ProviderStatus.PRODUCTION, mlt.status());
        assertFalse(mlt.autoDispatch());
        assertFalse(ProviderEligibility.isEligible(mlt, productionJob(List.of("timeline_render"))));
    }

    @Test
    void blenderIsNotProductionEligible() {
        ProviderMetadata blender = blenderMetadata();
        assertNotEquals(ProviderStatus.PRODUCTION, blender.status());
        assertFalse(blender.autoDispatch());
        assertFalse(ProviderEligibility.isEligible(blender, productionJob(List.of("3d_render"))));
    }

    @Test
    void natronIsNotProductionEligible() {
        ProviderMetadata natron = natronMetadata();
        assertNotEquals(ProviderStatus.PRODUCTION, natron.status());
        assertFalse(natron.autoDispatch());
        assertFalse(ProviderEligibility.isEligible(natron, productionJob(List.of("vfx_composite"))));
    }

    @Test
    void gstreamerIsNotProductionEligible() {
        ProviderMetadata gstreamer = gstreamerMetadata();
        assertNotEquals(ProviderStatus.PRODUCTION, gstreamer.status());
        assertFalse(gstreamer.autoDispatch());
        assertFalse(ProviderEligibility.isEligible(gstreamer, productionJob(List.of("streaming"))));
    }

    @Test
    void gpacIsNotProductionEligible() {
        ProviderMetadata gpac = gpacMetadata();
        assertNotEquals(ProviderStatus.PRODUCTION, gpac.status());
        assertFalse(gpac.autoDispatch());
        assertFalse(ProviderEligibility.isEligible(gpac, productionJob(List.of("package_hls"))));
    }

    // ── Provider status enum completeness ──

    @Test
    void providerStatusHasExpectedValues() {
        ProviderStatus[] values = ProviderStatus.values();
        assertEquals(9, values.length, "ProviderStatus must have exactly 9 values");

        assertNotNull(ProviderStatus.PRODUCTION);
        assertNotNull(ProviderStatus.POC);
        assertNotNull(ProviderStatus.OPTIONAL);
        assertNotNull(ProviderStatus.STUB);
        assertNotNull(ProviderStatus.SKELETON);
        assertNotNull(ProviderStatus.HOLD);
        assertNotNull(ProviderStatus.SPIKE);
        assertNotNull(ProviderStatus.DEPRECATED);
        assertNotNull(ProviderStatus.MOCK);
    }

    @Test
    void onlyProductionStatusIsAutoDispatchEligible() {
        for (ProviderStatus status : ProviderStatus.values()) {
            if (status == ProviderStatus.PRODUCTION) {
                assertTrue(status.isProductionDispatchEligible(),
                        "PRODUCTION must be auto-dispatch eligible");
            } else {
                assertFalse(status.isProductionDispatchEligible(),
                        status.name() + " must NOT be auto-dispatch eligible");
            }
        }
    }

    @Test
    void stubSkeletonDeprecatedMockCannotBeConfiguredForDispatch() {
        ProviderStatus[] nonDispatchable = {
                ProviderStatus.STUB, ProviderStatus.SKELETON,
                ProviderStatus.DEPRECATED, ProviderStatus.MOCK
        };
        for (ProviderStatus status : nonDispatchable) {
            assertFalse(status.canBeConfiguredForDispatch(),
                    status.name() + " must NOT be configurable for dispatch");
        }
    }

    // ── Provider metadata safety ──

    @Test
    void providerMetadataDoesNotExposeSensitiveFields() {
        ProviderMetadata meta = ffmpegMetadata();

        String metaString = meta.toString();
        assertFalse(metaString.contains("secret"), "Metadata must not contain 'secret'");
        assertFalse(metaString.contains("password"), "Metadata must not contain 'password'");
        assertFalse(metaString.contains("token"), "Metadata must not contain 'token'");
        assertFalse(metaString.contains("signedUrl"), "Metadata must not contain 'signedUrl'");
    }

    @Test
    void providerMetadataHasSafeDefaults() {
        ProviderMetadata meta = new ProviderMetadata(
                "test", ProviderStatus.STUB, "P0", ProviderType.RENDER,
                List.of(), List.of(), List.of(), List.of(),
                false, "none", "Test", List.of());

        assertFalse(meta.autoDispatch(), "Default must not auto-dispatch");
        assertFalse(meta.isProduction(), "Default must not be production");
        assertFalse(meta.participatesInAutoRouting(), "Default must not participate in auto-routing");
    }
}
