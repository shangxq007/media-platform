package com.example.platform.render.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Tag("native-media")
class RenderQualityCheckServiceTest {

    private RenderQualityCheckService qualityCheckService;
    private JavaCVRenderProvider provider;

    private static JavaCVRenderProvider createJavaCVProvider() {
        JavaCVMediaProbeAdapter adapter = new JavaCVMediaProbeAdapter();
        MediaProbeService probeService = new MediaProbeService(adapter);
        return new JavaCVRenderProvider(new JavaCVRenderService(probeService), new JavaCVTranscodeService(probeService),
                new com.example.platform.render.domain.timeline.TimelineScriptParser());
    }

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        JavaCVMediaProbeAdapter adapter = new JavaCVMediaProbeAdapter();
        MediaProbeService probeService = new MediaProbeService(adapter);
        probeService.setStorageRoot(tempDir.toString());
        qualityCheckService = new RenderQualityCheckService(probeService);

        JavaCVRenderService renderService = new JavaCVRenderService(probeService);
        JavaCVTranscodeService transcodeService = new JavaCVTranscodeService(probeService);
        provider = new JavaCVRenderProvider(renderService, transcodeService,
                new com.example.platform.render.domain.timeline.TimelineScriptParser());
        provider.setStorageRoot(tempDir.toString());
    }

    @Test
    void validMP4PassesQualityCheck(@TempDir Path tempDir) {
        provider.render("job-1", "{\"tracks\":[]}", "default_720p");

        RenderProviderProfile profile = new RenderProviderProfile(
                "test_720p", "Test 720p", "1280x720", 30, "mp4", "h264", "aac",
                60, false, "FREE", Set.of(), false
        );

        RenderQualityCheckService.QualityCheckResult result =
                qualityCheckService.check("job-1", "artifacts/job-1/output.mp4", profile);

        assertTrue(result.passed(), "Quality check should pass: " + result.message());
    }

    @Test
    void wrongResolutionFails(@TempDir Path tempDir) {
        provider.render("job-2", "{\"tracks\":[]}", "default_720p");

        RenderProviderProfile profile = new RenderProviderProfile(
                "test_1080p", "Test 1080p", "1920x1080", 30, "mp4", "h264", "aac",
                60, false, "PRO", Set.of(), false
        );

        RenderQualityCheckService.QualityCheckResult result =
                qualityCheckService.check("job-2", "artifacts/job-2/output.mp4", profile);

        assertFalse(result.passed());
        assertTrue(result.message().contains("Resolution mismatch"));
    }

    @Test
    void missingArtifactFails(@TempDir Path tempDir) {
        RenderProviderProfile profile = new RenderProviderProfile(
                "test", "Test", "1280x720", 30, "mp4", "h264", "aac",
                60, false, "FREE", Set.of(), false
        );

        RenderQualityCheckService.QualityCheckResult result =
                qualityCheckService.check("job-nonexistent", "artifacts/job-nonexistent/output.mp4", profile);

        assertFalse(result.passed());
        assertTrue(result.message().contains("PROBE_FAILED"));
    }

    @Test
    void effectDescriptorLookupWorks() {
        EffectMappingService mappingService = new EffectMappingService();

        assertTrue(mappingService.getDescriptor("video.fade_in").isPresent());
        assertTrue(mappingService.getDescriptor("video.blur").isPresent());
        assertTrue(mappingService.getDescriptor("text.subtitle_burn_in").isPresent());
        assertTrue(mappingService.getDescriptor("audio.volume").isPresent());
        assertTrue(mappingService.getDescriptor("nonexistent").isEmpty());
    }

    @Test
    void effectMappingResolvesNativeName() {
        EffectMappingService mappingService = new EffectMappingService();

        String nativeName = mappingService.resolveNativeName("video.blur", "ofx");
        assertNotNull(nativeName);
        assertEquals("Blur", nativeName);
    }

    @Test
    void providerCapabilityRegistryWorks() {
        RenderProviderRegistry registry = new RenderProviderRegistry();
        JavaCVRenderProvider javacv = createJavaCVProvider();
        OFXRenderProvider ofx = new OFXRenderProvider();

        registry.register("javacv", javacv, javacv.getCapability());
        registry.register("ofx", ofx, ofx.getCapability());

        assertEquals(2, registry.getAllProviders().size());
        assertTrue(registry.getProvider("javacv").isPresent());
        assertTrue(registry.getProvider("ofx").isPresent());

        RenderProviderCapability cap = registry.getCapability("javacv").orElse(null);
        assertNotNull(cap);
        assertEquals("javacv", cap.providerKey());
        assertFalse(cap.experimental());

        assertTrue(registry.getCapability("javacv").get().supportsEffect("video.fade_in"));
        assertTrue(registry.getCapability("ofx").get().supportsEffect("video.blur"));
    }

    @Test
    void selectionPolicySelectsCorrectProvider() {
        RenderProviderRegistry registry = new RenderProviderRegistry();
        JavaCVRenderProvider javacv = createJavaCVProvider();
        OFXRenderProvider ofx = new OFXRenderProvider();

        registry.register("javacv", javacv, javacv.getCapability());
        registry.register("ofx", ofx, ofx.getCapability());

        RenderProviderSelectionPolicy policy = new RenderProviderSelectionPolicy(
                registry, new com.example.platform.render.infrastructure.effects.EffectProviderRouter(
                        new EffectMappingService()));

        var result = policy.select("default_1080p", List.of());
        assertTrue(result.isPresent());
        assertInstanceOf(JavaCVRenderProvider.class, result.get());

        result = policy.select("ofx_1080p", List.of());
        assertTrue(result.isPresent());
        assertInstanceOf(OFXRenderProvider.class, result.get());
    }

    @Test
    void fallbackPolicyReturnsProvider() {
        RenderProviderRegistry registry = new RenderProviderRegistry();
        JavaCVRenderProvider javacv = createJavaCVProvider();

        registry.register("javacv", javacv, javacv.getCapability());

        RenderProviderSelectionPolicy selectionPolicy = new RenderProviderSelectionPolicy(
                registry, new com.example.platform.render.infrastructure.effects.EffectProviderRouter(
                        new EffectMappingService()));
        RenderProviderFallbackPolicy fallbackPolicy = new RenderProviderFallbackPolicy(registry, selectionPolicy);

        RenderProvider provider = fallbackPolicy.resolve("default_1080p", List.of());
        assertInstanceOf(JavaCVRenderProvider.class, provider);
    }
}
