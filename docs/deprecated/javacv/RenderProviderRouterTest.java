package com.example.platform.render.infrastructure;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("native-media")
class RenderProviderRouterTest {

    private JavaCVRenderProvider createJavaCVProvider() {
        JavaCVMediaProbeAdapter adapter = new JavaCVMediaProbeAdapter();
        MediaProbeService probeService = new MediaProbeService(adapter);
        return new JavaCVRenderProvider(new JavaCVRenderService(probeService), new JavaCVTranscodeService(probeService),
                new com.example.platform.render.domain.timeline.TimelineScriptParser());
    }

    @Test
    void registryBasedRoutingWorks() {
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
        assertInstanceOf(OFXRenderProvider.class, result);
    }

    @Test
    void standardProfileRoutesToJavaCV() {
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
        assertInstanceOf(JavaCVRenderProvider.class, result);
    }

    @Test
    void effectBasedRoutingUsesOFX() {
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

        RenderProvider result = router.route("ofx_1080p", List.of("video.blur"));
        assertInstanceOf(OFXRenderProvider.class, result);
    }

    @Test
    void fallbackWhenNoEffectsUsesJavaCV() {
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

        RenderProvider result = router.route("default_1080p", List.of());
        assertInstanceOf(JavaCVRenderProvider.class, result);
    }

    @Test
    void fallbackProviderWorksWhenPreferredUnavailable() {
        RenderProviderRegistry registry = new RenderProviderRegistry();
        JavaCVRenderProvider javacv = createJavaCVProvider();

        registry.register("javacv", javacv, javacv.getCapability());

        RenderProviderSelectionPolicy selectionPolicy = new RenderProviderSelectionPolicy(
                registry, new com.example.platform.render.infrastructure.effects.EffectProviderRouter(
                        new EffectMappingService()));
        RenderProviderFallbackPolicy fallbackPolicy = new RenderProviderFallbackPolicy(registry, selectionPolicy);
        RenderProviderRouter router = new RenderProviderRouter(
                new RenderProviderResolver(registry, selectionPolicy),
                fallbackPolicy,
                new com.example.platform.render.domain.timeline.TimelineExtensionsReader());

        RenderProvider result = router.route("default_1080p");
        assertInstanceOf(JavaCVRenderProvider.class, result);
    }

    @Test
    void registryReturnsCorrectCapabilities() {
        RenderProviderRegistry registry = new RenderProviderRegistry();
        JavaCVRenderProvider javacv = createJavaCVProvider();
        OFXRenderProvider ofx = new OFXRenderProvider();

        registry.register("javacv", javacv, javacv.getCapability());
        registry.register("ofx", ofx, ofx.getCapability());

        assertEquals(2, registry.getAllCapabilities().size());
        assertTrue(registry.getCapability("javacv").isPresent());
        assertTrue(registry.getCapability("ofx").isPresent());

        RenderProviderCapability javacvCap = registry.getCapability("javacv").get();
        assertFalse(javacvCap.experimental());
        assertTrue(javacvCap.supportsEffect("video.fade_in"));

        RenderProviderCapability ofxCap = registry.getCapability("ofx").get();
        assertTrue(ofxCap.supportsEffect("video.vignette"));
    }
}
