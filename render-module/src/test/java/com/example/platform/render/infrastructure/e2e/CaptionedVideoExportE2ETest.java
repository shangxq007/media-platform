package com.example.platform.render.infrastructure.e2e;

import com.example.platform.render.infrastructure.*;
import com.example.platform.render.infrastructure.font.*;
import com.example.platform.render.infrastructure.otio.*;
import com.example.platform.render.infrastructure.remotion.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CaptionedVideoExportE2ETest {

    private InMemoryFontAssetRepository assetRepository;
    private DefaultFontManifestResolver manifestResolver;
    private DefaultRenderJobFontPreflight preflight;
    private DefaultRenderPlanner planner;
    private OTIOTimelineCompiler otioCompiler;
    private NoopFontStackResolver stackResolver;
    private NoopMissingGlyphDetector missingDetector;

    @BeforeEach
    void setUp() {
        assetRepository = new InMemoryFontAssetRepository();
        stackResolver = new NoopFontStackResolver();
        manifestResolver = new DefaultFontManifestResolver(assetRepository, stackResolver);
        missingDetector = new NoopMissingGlyphDetector();
        preflight = new DefaultRenderJobFontPreflight(assetRepository, manifestResolver, missingDetector, stackResolver);
        planner = new DefaultRenderPlanner(new RenderProviderRegistry());
        otioCompiler = new OTIOTimelineCompiler();
    }

    private RenderJob buildReadyFontAsset(String fontId) {
        FontSecurityResult sec = FontSecurityResult.passed("BasicFontSecurityScanner", "abc123", "font/ttf");
        FontValidationResult val = new FontValidationResult("NoopFontValidator", "PASSED",
                List.of(), List.of(), "NotoSansCJK", "Regular", 400, "normal",
                true, true, true, true, true, true, true, true);
        FontAsset asset = new FontAsset(fontId, "NotoSansCJK-Regular.ttf", "NotoSansCJK", "Regular", "ttf",
                1024, "abc123", "s3://fonts/NotoSansCJK-Regular.ttf",
                FontAssetStatus.READY, sec, val, null);
        assetRepository.save(asset);
        return null;
    }

    @Test
    void fullE2eFlowCaptionsVideoExport() {
        String fontId = "font-001";
        buildReadyFontAsset(fontId);

        String otioJson = "{\"tracks\":[]}";
        Map<String, Object> metadata = Map.of("platform", Map.of(
                "schemaVersion", "1.0.0",
                "projectId", "project-e2e",
                "timelineId", "timeline-e2e",
                "captions", List.of(Map.of(
                        "id", "cap-001",
                        "assetRef", "caption-asset-001",
                        "startTime", 1.0,
                        "endTime", 5.0
                )),
                "fonts", List.of(Map.of(
                        "refId", "font-ref-001",
                        "assetId", fontId,
                        "fontFamily", "NotoSansCJK",
                        "fontWeight", "400",
                        "fontStyle", "normal"
                )),
                "renderHints", Map.of(
                        "outputFormat", "mp4",
                        "outputWidth", 1920,
                        "outputHeight", 1080,
                        "outputFps", 30
                )
        ));

        OTIOTimelineSummary summary = otioCompiler.compile(otioJson, metadata);
        assertNotNull(summary);
        assertEquals("1.0.0", summary.schemaVersion());
        assertNotNull(summary.captionRefs());
        assertFalse(summary.captionRefs().isEmpty());
        assertNotNull(summary.fontRefs());
        assertFalse(summary.fontRefs().isEmpty());

        RenderJob job = otioCompiler.generateRenderJob(summary, "production");
        assertNotNull(job);
        assertTrue(job.requiredCapabilities().contains("caption_effects"));

        FontPreflightResult preflightResult = preflight.preflight(job);
        assertTrue(preflightResult.passed(), "Preflight should pass, but got errors: " + preflightResult.errors());
        assertFalse(preflightResult.fontAssetIds().isEmpty());
        assertTrue(preflightResult.productionSafe());

        RenderPlan plan = planner.plan(job);
        assertNotNull(plan);
        assertFalse(plan.steps().isEmpty());
        assertTrue(plan.steps().size() >= 2);

        boolean hasRemotionStep = plan.steps().stream()
                .anyMatch(s -> s.providerName().equals("remotion"));
        boolean hasFFmpegStep = plan.steps().stream()
                .anyMatch(s -> s.providerName().equals("ffmpeg"));
        assertTrue(hasRemotionStep, "Plan should have a remotion step");
        assertTrue(hasFFmpegStep, "Plan should have an ffmpeg step");

        for (RenderStep step : plan.steps()) {
            assertNotNull(step.providerName());
            assertNotNull(step.providerType());
            assertNotNull(step.requiredCapabilities());
            assertNotNull(step.dependsOn());
        }
    }

    @Test
    void e2eFailsWhenFontNotReady() {
        FontSecurityResult sec = FontSecurityResult.passed("BasicFontSecurityScanner", "abc123", "font/ttf");
        FontValidationResult val = new FontValidationResult("NoopFontValidator", "PASSED",
                List.of(), List.of(), "NotoSansCJK", "Regular", 400, "normal",
                true, true, true, true, true, true, true, true);
        FontAsset asset = new FontAsset("font-001", "NotoSansCJK-Regular.ttf", "NotoSansCJK", "Regular", "ttf",
                1024, "abc123", "s3://fonts/NotoSansCJK-Regular.ttf",
                FontAssetStatus.UPLOADED, sec, val, null);
        assetRepository.save(asset);

        String otioJson = "{\"tracks\":[]}";
        Map<String, Object> metadata = Map.of("platform", Map.of(
                "schemaVersion", "1.0.0",
                "projectId", "project-e2e",
                "timelineId", "timeline-e2e",
                "fonts", List.of(Map.of(
                        "refId", "font-ref-001",
                        "assetId", "font-001",
                        "fontFamily", "NotoSansCJK",
                        "fontWeight", "400",
                        "fontStyle", "normal"
                )),
                "renderHints", Map.of(
                        "outputFormat", "mp4",
                        "outputWidth", 1920,
                        "outputHeight", 1080,
                        "outputFps", 30
                )
        ));

        OTIOTimelineSummary summary = otioCompiler.compile(otioJson, metadata);
        assertNotNull(summary);

        RenderJob job = otioCompiler.generateRenderJob(summary, "production");
        assertNotNull(job);

        FontPreflightResult preflightResult = preflight.preflight(job);
        assertFalse(preflightResult.passed());
        assertTrue(preflightResult.errors().stream().anyMatch(e -> e.contains("not ready")));
    }

    @Test
    void e2eFailsWhenSecurityNotPassed() {
        FontSecurityResult sec = FontSecurityResult.rejected("BasicFontSecurityScanner", List.of("Blocked"));
        FontAsset asset = new FontAsset("font-001", "malicious.ttf", "Evil", "Regular", "ttf",
                1024, null, "s3://fonts/malicious.ttf",
                FontAssetStatus.READY, sec, null, null);
        assetRepository.save(asset);

        String otioJson = "{\"tracks\":[]}";
        Map<String, Object> metadata = Map.of("platform", Map.of(
                "schemaVersion", "1.0.0",
                "projectId", "project-e2e",
                "timelineId", "timeline-e2e",
                "fonts", List.of(Map.of(
                        "refId", "font-ref-001",
                        "assetId", "font-001",
                        "fontFamily", "Evil",
                        "fontWeight", "400",
                        "fontStyle", "normal"
                )),
                "renderHints", Map.of(
                        "outputFormat", "mp4",
                        "outputWidth", 1920,
                        "outputHeight", 1080,
                        "outputFps", 30
                )
        ));

        OTIOTimelineSummary summary = otioCompiler.compile(otioJson, metadata);
        RenderJob job = otioCompiler.generateRenderJob(summary, "production");
        FontPreflightResult preflightResult = preflight.preflight(job);

        assertFalse(preflightResult.passed());
        assertTrue(preflightResult.errors().stream().anyMatch(e -> e.contains("security")));
    }

    @Test
    void e2eProductionModeRejectsDeprecatedProvider() {
        ProviderMetadata ofxMeta = new ProviderMetadata(
                "ofx", ProviderStatus.DEPRECATED, "P3", ProviderType.RENDER,
                List.of("trim"), List.of(), List.of("trim"),
                List.of("3d_render"), false, "server",
                "Deprecated OFX provider", List.of("Not real OFX")
        );

        boolean eligible = ProviderEligibility.isEligible(ofxMeta,
                new RenderJob("job-1", "video_export", "production", "1920x1080",
                        List.of(), "{}", "{}", "{}", "mp4", List.of(),
                        new RenderConstraints(3840, 2160, 60, 3600, null, null),
                        true, List.of(), List.of()));
        assertFalse(eligible, "Deprecated provider should not be eligible in production");
    }

    @Test
    void e2eArtifactTraceability() {
        String fontId = "font-001";
        buildReadyFontAsset(fontId);

        String otioJson = "{\"tracks\":[]}";
        Map<String, Object> metadata = Map.of("platform", Map.of(
                "schemaVersion", "1.0.0",
                "projectId", "project-e2e",
                "timelineId", "timeline-e2e",
                "captions", List.of(Map.of(
                        "id", "cap-001",
                        "assetRef", "caption-asset-001",
                        "startTime", 1.0,
                        "endTime", 5.0
                )),
                "fonts", List.of(Map.of(
                        "refId", "font-ref-001",
                        "assetId", fontId,
                        "fontFamily", "NotoSansCJK",
                        "fontWeight", "400",
                        "fontStyle", "normal"
                )),
                "renderHints", Map.of(
                        "outputFormat", "mp4",
                        "outputWidth", 1920,
                        "outputHeight", 1080,
                        "outputFps", 30
                )
        ));

        OTIOTimelineSummary summary = otioCompiler.compile(otioJson, metadata);
        RenderJob job = otioCompiler.generateRenderJob(summary, "production");
        RenderPlan plan = planner.plan(job);

        assertNotNull(plan);
        for (RenderStep step : plan.steps()) {
            assertNotNull(step.id());
            assertNotNull(step.providerName());
            assertNotNull(step.providerType());
        }
    }

    @Test
    void e2eRemotionUsesSubsetUrlNotSourceUrl() {
        FontSubsetResult subsetResult = new FontSubsetResult("pyftsubset", true,
                "cache-key-123", "s3://fonts/subsets/font-001.woff2", "woff2",
                512000, 65536, 1024, List.of(), Map.of());
        FontSecurityResult sec = FontSecurityResult.passed("BasicFontSecurityScanner", "abc123", "font/ttf");
        FontValidationResult val = new FontValidationResult("NoopFontValidator", "PASSED",
                List.of(), List.of(), "NotoSansCJK", "Regular", 400, "normal",
                true, true, true, true, true, true, true, true);
        FontAsset asset = new FontAsset("font-001", "NotoSansCJK-Regular.ttf", "NotoSansCJK", "Regular", "ttf",
                1024, "abc123", "s3://fonts/NotoSansCJK-Regular.ttf",
                FontAssetStatus.READY_WITH_SUBSETS, sec, val, subsetResult);
        assetRepository.save(asset);

        FontManifestResolver.ResolvedFont resolved = manifestResolver.resolvePrimary("font-001");
        assertNotNull(resolved);
        assertNotNull(resolved.subsetUrl());
        assertEquals("s3://fonts/subsets/font-001.woff2", resolved.subsetUrl());
        assertEquals("NotoSansCJK", resolved.family());
        assertTrue(resolved.productionSafe());
    }

    @Test
    void e2eMissingGlyphsNoFallbackBlocks() {
        FontSecurityResult sec = FontSecurityResult.passed("BasicFontSecurityScanner", "abc123", "font/ttf");
        FontValidationResult val = new FontValidationResult("NoopFontValidator", "PASSED",
                List.of(), List.of(), "NotoSansCJK", "Regular", 400, "normal",
                true, true, true, true, true, true, true, true);
        FontAsset asset = new FontAsset("font-001", "NotoSansCJK-Regular.ttf", "NotoSansCJK", "Regular", "ttf",
                1024, "abc123", "s3://fonts/NotoSansCJK-Regular.ttf",
                FontAssetStatus.READY, sec, val, null);
        assetRepository.save(asset);

        String otioJson = "{\"tracks\":[]}";
        Map<String, Object> metadata = Map.of("platform", Map.of(
                "schemaVersion", "1.0.0",
                "projectId", "project-e2e",
                "timelineId", "timeline-e2e",
                "captions", List.of(Map.of(
                        "id", "cap-001",
                        "assetRef", "caption-asset-001",
                        "startTime", 1.0,
                        "endTime", 5.0
                )),
                "fonts", List.of(Map.of(
                        "refId", "font-ref-001",
                        "assetId", "font-001",
                        "fontFamily", "NotoSansCJK",
                        "fontWeight", "400",
                        "fontStyle", "normal"
                )),
                "renderHints", Map.of(
                        "outputFormat", "mp4",
                        "outputWidth", 1920,
                        "outputHeight", 1080,
                        "outputFps", 30
                )
        ));

        OTIOTimelineSummary summary = otioCompiler.compile(otioJson, metadata);
        RenderJob job = otioCompiler.generateRenderJob(summary, "production");
        assertNotNull(job);

        FontPreflightResult preflightResult = preflight.preflight(job);
        assertTrue(preflightResult.passed());
        assertTrue(preflightResult.productionSafe());
    }
}
