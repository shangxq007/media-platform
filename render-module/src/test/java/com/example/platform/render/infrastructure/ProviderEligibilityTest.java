package com.example.platform.render.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProviderEligibilityTest {

    private ProviderMetadata productionProvider(String name, String priority, List<String> enabledCaps) {
        return new ProviderMetadata(name, ProviderStatus.PRODUCTION, priority, ProviderType.RENDER,
                List.of("trim", "transcode"), enabledCaps, List.of(), List.of(), true, "server",
                "Test provider", List.of());
    }

    private ProviderMetadata pocProvider(String name, String priority, List<String> enabledCaps) {
        return new ProviderMetadata(name, ProviderStatus.POC, priority, ProviderType.RENDER,
                List.of("trim", "transcode"), enabledCaps, List.of(), List.of(), true, "server",
                "Test provider", List.of());
    }

    private ProviderMetadata deprecatedProvider(String name, List<String> enabledCaps) {
        return new ProviderMetadata(name, ProviderStatus.DEPRECATED, "P3", ProviderType.RENDER,
                List.of("trim"), enabledCaps, List.of(), List.of(), false, "server",
                "Deprecated provider", List.of());
    }

    private ProviderMetadata holdProvider(String name, List<String> enabledCaps) {
        return new ProviderMetadata(name, ProviderStatus.HOLD, "P2", ProviderType.RENDER,
                List.of("trim"), enabledCaps, List.of(), List.of(), false, "server",
                "Hold provider", List.of());
    }

    private ProviderMetadata spikeProvider(String name, List<String> enabledCaps) {
        return new ProviderMetadata(name, ProviderStatus.SPIKE, "P2", ProviderType.RENDER,
                List.of("trim"), enabledCaps, List.of(), List.of(), false, "server",
                "Spike provider", List.of());
    }

    private ProviderMetadata noAutoDispatchProvider(String name, List<String> enabledCaps) {
        return new ProviderMetadata(name, ProviderStatus.PRODUCTION, "P0", ProviderType.RENDER,
                List.of("trim"), enabledCaps, List.of(), List.of(), false, "server",
                "No auto-dispatch provider", List.of());
    }

    private RenderJob productionJob(List<String> requiredCaps) {
        return new RenderJob("job-1", "video_export", "production", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", requiredCaps,
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }

    private RenderJob experimentJob(List<String> requiredCaps) {
        return new RenderJob("job-2", "video_export", "experiment", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", requiredCaps,
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }

    private RenderJob manualJob(List<String> requiredCaps) {
        return new RenderJob("job-3", "video_export", "manual", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", requiredCaps,
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }

    @Test
    void deprecatedProviderNeverScheduled() {
        ProviderMetadata meta = deprecatedProvider("deprecated-p", List.of("trim"));
        assertFalse(ProviderEligibility.isEligible(meta, productionJob(List.of("trim"))));
        assertFalse(ProviderEligibility.isEligible(meta, experimentJob(List.of("trim"))));
        assertFalse(ProviderEligibility.isEligible(meta, manualJob(List.of("trim"))));
    }

    @Test
    void holdProviderNotInProduction() {
        ProviderMetadata meta = holdProvider("hold-p", List.of("trim"));
        assertFalse(ProviderEligibility.isEligible(meta, productionJob(List.of("trim"))));
        assertTrue(ProviderEligibility.isEligible(meta, experimentJob(List.of("trim"))));
        assertTrue(ProviderEligibility.isEligible(meta, manualJob(List.of("trim"))));
    }

    @Test
    void holdProviderWithManualMode() {
        ProviderMetadata meta = new ProviderMetadata("hold-p", ProviderStatus.HOLD, "P2",
                ProviderType.RENDER, List.of("trim"), List.of("trim"), List.of(),
                List.of(), false, "server", "Hold", List.of());
        assertFalse(ProviderEligibility.isEligible(meta, productionJob(List.of("trim"))));
        assertTrue(ProviderEligibility.isEligible(meta, experimentJob(List.of("trim"))));
        assertTrue(ProviderEligibility.isEligible(meta, manualJob(List.of("trim"))));
    }

    @Test
    void spikeProviderNotInAutoRouting() {
        ProviderMetadata meta = spikeProvider("spike-p", List.of("trim"));
        assertFalse(ProviderEligibility.isEligible(meta, productionJob(List.of("trim"))));
        assertFalse(ProviderEligibility.isEligible(meta, experimentJob(List.of("trim"))));
        assertTrue(ProviderEligibility.isEligible(meta, manualJob(List.of("trim"))));
    }

    @Test
    void noAutoDispatchNotInProduction() {
        ProviderMetadata meta = noAutoDispatchProvider("noauto-p", List.of("trim"));
        assertFalse(ProviderEligibility.isEligible(meta, productionJob(List.of("trim"))));
        assertTrue(ProviderEligibility.isEligible(meta, manualJob(List.of("trim"))));
    }

    @Test
    void enabledCapabilitiesMustMatchRequired() {
        ProviderMetadata meta = productionProvider("ffmpeg", "P0", List.of("trim", "transcode"));
        assertTrue(ProviderEligibility.isEligible(meta, productionJob(List.of("trim"))));
        assertTrue(ProviderEligibility.isEligible(meta, productionJob(List.of("trim", "transcode"))));
        assertFalse(ProviderEligibility.isEligible(meta, productionJob(List.of("3d_render"))));
    }

    @Test
    void declaredCapabilitiesNotUsedForScheduling() {
        ProviderMetadata meta = new ProviderMetadata("test", ProviderStatus.PRODUCTION, "P0",
                ProviderType.RENDER,
                List.of("trim", "transcode", "3d_render"),
                List.of("trim"),
                List.of("transcode", "3d_render"),
                List.of(), true, "server", "Test", List.of());
        assertTrue(ProviderEligibility.isEligible(meta, productionJob(List.of("trim"))));
        assertFalse(ProviderEligibility.isEligible(meta, productionJob(List.of("3d_render"))));
        assertFalse(ProviderEligibility.isEligible(meta, productionJob(List.of("transcode"))));
    }

    @Test
    void notForBlocksSelection() {
        ProviderMetadata meta = new ProviderMetadata("ffmpeg", ProviderStatus.PRODUCTION, "P0",
                ProviderType.RENDER,
                List.of("trim", "transcode"), List.of("trim", "transcode"), List.of(),
                List.of("3d_render", "template_render"), true, "server", "Test", List.of());
        assertTrue(ProviderEligibility.isEligible(meta, productionJob(List.of("trim"))));
        assertFalse(ProviderEligibility.isEligible(meta, productionJob(List.of("3d_render"))));
        assertFalse(ProviderEligibility.isEligible(meta, productionJob(List.of("template_render"))));
    }

    @Test
    void productionPreferredOverPoc() {
        ProviderMetadata prod = productionProvider("ffmpeg", "P0", List.of("trim"));
        ProviderMetadata poc = pocProvider("mlt", "P1", List.of("trim"));
        assertTrue(ProviderEligibility.scoreProvider(prod, productionJob(List.of("trim")))
                < ProviderEligibility.scoreProvider(poc, productionJob(List.of("trim"))));
    }

    @Test
    void p0PreferredOverP1() {
        ProviderMetadata p0 = productionProvider("ffmpeg", "P0", List.of("trim"));
        ProviderMetadata p1 = pocProvider("mlt", "P1", List.of("trim"));
        assertTrue(ProviderEligibility.scoreProvider(p0, productionJob(List.of("trim")))
                < ProviderEligibility.scoreProvider(p1, productionJob(List.of("trim"))));
    }

    @Test
    void p1PreferredOverP2() {
        ProviderMetadata p1 = pocProvider("mlt", "P1", List.of("trim"));
        ProviderMetadata p2 = pocProvider("gstreamer", "P2", List.of("trim"));
        assertTrue(ProviderEligibility.scoreProvider(p1, productionJob(List.of("trim")))
                < ProviderEligibility.scoreProvider(p2, productionJob(List.of("trim"))));
    }

    @Test
    void bmfSpikeCannotBeAutoScheduled() {
        ProviderMetadata bmf = new ProviderMetadata("bmf", ProviderStatus.SPIKE, "P2",
                ProviderType.MEDIA_PIPELINE,
                List.of(Capabilities.MEDIA_PIPELINE, Capabilities.AI_MEDIA_PIPELINE),
                List.of(),
                List.of(Capabilities.MEDIA_PIPELINE, Capabilities.AI_MEDIA_PIPELINE),
                List.of(Capabilities.CAPTION_EFFECTS, Capabilities.TEMPLATE_RENDER,
                        Capabilities.RENDER_3D, Capabilities.CLOUD_RENDER),
                false, "server", "BMF research", List.of());
        assertFalse(ProviderEligibility.isEligible(bmf, productionJob(List.of("trim"))));
        assertFalse(ProviderEligibility.isEligible(bmf, experimentJob(List.of("trim"))));
        assertFalse(ProviderEligibility.isEligible(bmf, manualJob(List.of(Capabilities.MEDIA_PIPELINE))));
        assertTrue(ProviderEligibility.isEligible(bmf, manualJob(java.util.Collections.<String>emptyList())));
    }

    @Test
    void bmfSpikeWithEmptyCapabilities() {
        ProviderMetadata bmf = new ProviderMetadata("bmf", ProviderStatus.SPIKE, "P2",
                ProviderType.MEDIA_PIPELINE,
                List.of(Capabilities.MEDIA_PIPELINE),
                List.of(),
                List.of(Capabilities.MEDIA_PIPELINE),
                List.of(Capabilities.CAPTION_EFFECTS, Capabilities.TEMPLATE_RENDER),
                false, "server", "BMF research", List.of());
        assertTrue(ProviderEligibility.isEligible(bmf, manualJob(java.util.Collections.<String>emptyList())));
    }

    @Test
    void blockedProviderNotEligible() {
        ProviderMetadata meta = productionProvider("ffmpeg", "P0", List.of("trim"));
        RenderJob job = new RenderJob("job-1", "video_export", "production", "1920x1080",
                List.of(), "{}", "{}", "{}", "mp4", List.of("trim"),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of("ffmpeg"));
        assertFalse(ProviderEligibility.isEligible(meta, job));
    }

    @Test
    void preferredProviderGetsBetterScore() {
        ProviderMetadata preferred = productionProvider("ffmpeg", "P0", List.of("trim"));
        ProviderMetadata other = productionProvider("mlt", "P0", List.of("trim"));
        RenderJob job = new RenderJob("job-1", "video_export", "production", "1920x1080",
                List.of(), "{}", "{}", "{}", "mp4", List.of("trim"),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of("ffmpeg"), List.of());
        assertTrue(ProviderEligibility.scoreProvider(preferred, job)
                < ProviderEligibility.scoreProvider(other, job));
    }
}
