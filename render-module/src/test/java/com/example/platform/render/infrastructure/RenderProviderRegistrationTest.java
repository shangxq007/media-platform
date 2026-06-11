package com.example.platform.render.infrastructure;

import com.example.platform.render.infrastructure.ffmpeg.FFmpegRenderProvider;
import com.example.platform.render.infrastructure.gpac.GPACRenderProvider;
import com.example.platform.render.infrastructure.gstreamer.GStreamerRenderProvider;
import com.example.platform.render.infrastructure.mlt.MltRenderProvider;
import org.junit.jupiter.api.Test;


import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RenderProviderRegistrationTest {

    private JavaCVRenderProvider createJavaCVProvider() {
        JavaCVMediaProbeAdapter adapter = new JavaCVMediaProbeAdapter();
        MediaProbeService probeService = new MediaProbeService(adapter);
        return new JavaCVRenderProvider(new JavaCVRenderService(probeService), new JavaCVTranscodeService(probeService),
                new com.example.platform.render.domain.timeline.TimelineScriptParser());
    }

    @Test
    void allCoreProvidersCanRegister() {
        RenderProviderRegistry registry = new RenderProviderRegistry();
        JavaCVRenderProvider javacv = createJavaCVProvider();
        OFXRenderProvider ofx = new OFXRenderProvider();
        MockRenderProvider mock = new MockRenderProvider();

        registry.register("javacv", javacv, javacv.getCapability());
        registry.register("ofx", ofx, ofx.getCapability());
        registry.register("mock", mock, RenderProviderCapability.legacy(
                "mock",
                Set.of("mp4"),
                Set.of("h264", "aac"),
                Set.of("video.fade_in", "video.fade_out"),
                Set.of("dissolve"),
                Set.of("burn_in"),
                "1920x1080",
                false,
                false,
                true,
                Set.of("test_mock")
        ));

        assertEquals(3, registry.getAllProviders().size());
        assertTrue(registry.getProvider("javacv").isPresent());
        assertTrue(registry.getProvider("ofx").isPresent());
        assertTrue(registry.getProvider("mock").isPresent());
    }

    @Test
    void ffmpegProviderRegistersCorrectly() {
        FFmpegRenderProvider capability = new FFmpegRenderProvider(null, null,
                new com.example.platform.render.domain.timeline.TimelineScriptParser(), null);
        assertNotNull(capability);
        assertTrue(capability.supports("h264"));
        assertTrue(capability.supports("mp4"));
        assertTrue(capability.supports("watermark"));
    }

    @Test
    void gstreamerProviderRegistersCorrectly() {
        GStreamerRenderProvider capability = new GStreamerRenderProvider(null, null,
                new com.example.platform.render.domain.timeline.TimelineScriptParser());
        assertNotNull(capability);
        assertTrue(capability.supports("pipeline"));
        assertTrue(capability.supports("streaming"));
        assertTrue(capability.supports("real-time"));
    }

    @Test
    void gpacProviderRegistersCorrectly() {
        GPACRenderProvider capability = new GPACRenderProvider(null, null,
                new com.example.platform.render.domain.timeline.TimelineScriptParser());
        assertNotNull(capability);
        assertTrue(capability.supports("mp4"));
        assertTrue(capability.supports("dash"));
        assertTrue(capability.supports("hls"));
        assertTrue(capability.supports("cmaf"));
    }

    @Test
    void mltProviderRegistersCorrectly() {
        MltRenderProvider capability = new MltRenderProvider(null, null, null,
                new com.example.platform.render.domain.timeline.TimelineScriptParser());
        assertNotNull(capability);
        assertTrue(capability.supports("timeline"));
        assertTrue(capability.supports("multi-track"));
    }

    @Test
    void routerCanRouteToEnabledProviders() {
        RenderProviderRegistry registry = new RenderProviderRegistry();
        JavaCVRenderProvider javacv = createJavaCVProvider();
        OFXRenderProvider ofx = new OFXRenderProvider();

        registry.register("javacv", javacv, javacv.getCapability());
        registry.register("ofx", ofx, ofx.getCapability());

        RenderProviderSelectionPolicy selectionPolicy = new RenderProviderSelectionPolicy(
                registry, new com.example.platform.render.infrastructure.effects.EffectProviderRouter(
                        new EffectMappingService()));
        RenderProviderFallbackPolicy fallbackPolicy = new RenderProviderFallbackPolicy(registry, selectionPolicy);
        RenderProviderRouter router = new RenderProviderRouter(
                new RenderProviderResolver(registry, selectionPolicy),
                fallbackPolicy,
                new com.example.platform.render.domain.timeline.TimelineExtensionsReader());

        RenderProvider result = router.route("default_1080p");
        assertNotNull(result);
        assertInstanceOf(JavaCVRenderProvider.class, result);
    }

    @Test
    void routerRoutesToOfxForOfxProfiles() {
        RenderProviderRegistry registry = new RenderProviderRegistry();
        JavaCVRenderProvider javacv = createJavaCVProvider();
        OFXRenderProvider ofx = new OFXRenderProvider();

        registry.register("javacv", javacv, javacv.getCapability());
        registry.register("ofx", ofx, ofx.getCapability());

        RenderProviderSelectionPolicy selectionPolicy = new RenderProviderSelectionPolicy(
                registry, new com.example.platform.render.infrastructure.effects.EffectProviderRouter(
                        new EffectMappingService()));
        RenderProviderFallbackPolicy fallbackPolicy = new RenderProviderFallbackPolicy(registry, selectionPolicy);
        RenderProviderRouter router = new RenderProviderRouter(
                new RenderProviderResolver(registry, selectionPolicy),
                fallbackPolicy,
                new com.example.platform.render.domain.timeline.TimelineExtensionsReader());

        RenderProvider result = router.route("ofx_1080p");
        assertNotNull(result);
        assertInstanceOf(OFXRenderProvider.class, result);
    }

    @Test
    void providerKeysAreLowercase() {
        JavaCVRenderProvider javacv = createJavaCVProvider();
        OFXRenderProvider ofx = new OFXRenderProvider();

        assertEquals("javacv", javacv.getCapability().providerKey());
        assertEquals("ofx", ofx.getCapability().providerKey());
    }

    @Test
    void allProvidersHaveNonEmptyCapabilities() {
        JavaCVRenderProvider javacv = createJavaCVProvider();
        OFXRenderProvider ofx = new OFXRenderProvider();

        assertNotNull(javacv.getCapability());
        assertNotNull(ofx.getCapability());

        assertFalse(javacv.getCapability().supportedEffects().isEmpty());
        assertFalse(javacv.getCapability().supportedFormats().isEmpty());
        assertFalse(javacv.getCapability().supportedCodecs().isEmpty());
    }

    @Test
    void registryTracksMultipleProviders() {
        RenderProviderRegistry registry = new RenderProviderRegistry();
        JavaCVRenderProvider javacv = createJavaCVProvider();
        OFXRenderProvider ofx = new OFXRenderProvider();

        registry.register("javacv", javacv, javacv.getCapability());
        registry.register("ofx", ofx, ofx.getCapability());

        List<RenderProviderCapability> caps = registry.getAllCapabilities();
        assertEquals(2, caps.size());

        assertTrue(registry.getCapability("javacv").isPresent());
        assertTrue(registry.getCapability("ofx").isPresent());

        List<String> effects = registry.getAvailableEffects();
        assertFalse(effects.isEmpty());
    }

    @Test
    void ffmpegProviderIsNotDeadCode() {
        FFmpegRenderProvider provider = new FFmpegRenderProvider(null, null,
                new com.example.platform.render.domain.timeline.TimelineScriptParser(), null);
        assertNotNull(provider);
        assertFalse(provider.getSupportedProfiles().isEmpty());
        assertTrue(provider.supports("h264"));
        assertTrue(provider.supports("h265"));
        assertTrue(provider.supports("dash"));
        assertTrue(provider.supports("hls"));
    }

    @Test
    void gstreamerProviderIsNotDeadCode() {
        GStreamerRenderProvider provider = new GStreamerRenderProvider(null, null,
                new com.example.platform.render.domain.timeline.TimelineScriptParser());
        assertNotNull(provider);
        assertFalse(provider.getSupportedProfiles().isEmpty());
        assertTrue(provider.supports("pipeline"));
        assertTrue(provider.supports("streaming"));
    }

    @Test
    void gpacProviderIsNotDeadCode() {
        GPACRenderProvider provider = new GPACRenderProvider(null, null,
                new com.example.platform.render.domain.timeline.TimelineScriptParser());
        assertNotNull(provider);
        assertFalse(provider.getSupportedProfiles().isEmpty());
        assertTrue(provider.supports("mp4"));
        assertTrue(provider.supports("dash"));
        assertTrue(provider.supports("hls"));
    }

}
