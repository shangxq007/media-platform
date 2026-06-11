package com.example.platform.render.infrastructure.font;

import com.example.platform.render.infrastructure.font.*;
import com.example.platform.render.infrastructure.Capabilities;
import com.example.platform.render.infrastructure.RenderConstraints;
import com.example.platform.render.infrastructure.RenderJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RenderJobFontPreflightTest {

    private InMemoryFontAssetRepository assetRepository;
    private DefaultFontManifestResolver manifestResolver;
    private DefaultRenderJobFontPreflight preflight;
    private FontStackResolver noopStackResolver;

    @BeforeEach
    void setUp() {
        assetRepository = new InMemoryFontAssetRepository();
        noopStackResolver = new NoopFontStackResolver();
        manifestResolver = new DefaultFontManifestResolver(assetRepository, noopStackResolver);
        MissingGlyphDetector noopDetector = new NoopMissingGlyphDetector();
        preflight = new DefaultRenderJobFontPreflight(assetRepository, manifestResolver, noopDetector, noopStackResolver);
    }

    private RenderJob createJob(String fontRef, boolean allowDegrade, String mode) {
        String style = fontRef != null ? "{\"fontRef\":\"" + fontRef + "\"}" : "{}";
        return new RenderJob("job-1", "captioned_video_export", mode, "1920x1080",
                List.of(), "{}", "{}", style, "mp4",
                List.of(Capabilities.CAPTION_EFFECTS),
                new com.example.platform.render.infrastructure.RenderConstraints(3840, 2160, 60, 3600, null, null),
                allowDegrade, List.of(), List.of());
    }

    private FontAsset createAsset(String id, FontAssetStatus status, boolean productionSafe) {
        FontSecurityResult secResult = productionSafe
                ? FontSecurityResult.passed("BasicFontSecurityScanner", "abc123", "font/ttf")
                : new FontSecurityResult("NoopFontSecurityScanner", "WARNING_PASS", "2026-06-11T10:00:00Z",
                        false, List.of("Not production-safe"), null, null, false, false, false);
        FontValidationResult valResult = new FontValidationResult("NoopFontValidator", "PASSED",
                List.of(), List.of(), "TestFont", "Regular", 400, "normal",
                true, true, true, true, true, true, true, true);
        return new FontAsset(id, "test.ttf", "TestFont", "Regular", "ttf",
                1024, "abc123", "s3://fonts/test.ttf", status, secResult, valResult, null);
    }

    @Test
    void readyFontPassesPreflight() {
        FontAsset asset = createAsset("font-001", FontAssetStatus.READY, true);
        assetRepository.save(asset);
        FontPreflightResult result = preflight.preflight(createJob("font-001", true, "production"));
        assertTrue(result.passed());
        assertFalse(result.errors().isEmpty() == false);
        assertTrue(result.resolvedFonts().stream().anyMatch(f -> f.fontAssetId().equals("font-001")));
    }

    @Test
    void uploadedFontFailsPreflight() {
        FontAsset asset = createAsset("font-001", FontAssetStatus.UPLOADED, false);
        assetRepository.save(asset);
        FontPreflightResult result = preflight.preflight(createJob("font-001", true, "production"));
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("not ready")));
    }

    @Test
    void quarantinedFontFailsPreflight() {
        FontAsset asset = createAsset("font-001", FontAssetStatus.QUARANTINED, false);
        assetRepository.save(asset);
        FontPreflightResult result = preflight.preflight(createJob("font-001", true, "production"));
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("not ready")));
    }

    @Test
    void validationPendingFontFailsPreflight() {
        FontAsset asset = createAsset("font-001", FontAssetStatus.VALIDATION_PENDING, false);
        assetRepository.save(asset);
        FontPreflightResult result = preflight.preflight(createJob("font-001", true, "production"));
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("not ready")));
    }

    @Test
    void missingFontAssetFailsPreflight() {
        FontPreflightResult result = preflight.preflight(createJob("nonexistent-font", true, "production"));
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("not found")));
    }

    @Test
    void securityNotPassedFailsPreflight() {
        FontAsset asset = new FontAsset("font-001", "test.ttf", "TestFont", "Regular", "ttf",
                1024, null, "s3://fonts/test.ttf", FontAssetStatus.READY,
                FontSecurityResult.rejected("BasicFontSecurityScanner", List.of("Blocked")),
                null, null);
        assetRepository.save(asset);
        FontPreflightResult result = preflight.preflight(createJob("font-001", true, "production"));
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("security")));
    }

    @Test
    void notProductionSafeInProductionModeFailsPreflight() {
        FontSecurityResult secResult = new FontSecurityResult("BasicFontSecurityScanner", "PASSED",
                "2026-06-11T10:00:00Z", false, List.of("Not production-safe"),
                "abc123", "font/ttf", true, true, true);
        FontValidationResult valResult = new FontValidationResult("NoopFontValidator", "PASSED",
                List.of(), List.of(), "TestFont", "Regular", 400, "normal",
                true, true, true, true, true, true, true, true);
        FontAsset asset = new FontAsset("font-001", "test.ttf", "TestFont", "Regular", "ttf",
                1024, "abc123", "s3://fonts/test.ttf", FontAssetStatus.READY, secResult, valResult, null);
        assetRepository.save(asset);
        FontPreflightResult result = preflight.preflight(createJob("font-001", true, "production"));
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("not production-safe")));
    }

    @Test
    void notProductionSafeInExperimentModePassesPreflight() {
        FontSecurityResult secResult = new FontSecurityResult("BasicFontSecurityScanner", "PASSED",
                "2026-06-11T10:00:00Z", false, List.of("Not production-safe"),
                "abc123", "font/ttf", true, true, true);
        FontValidationResult valResult = new FontValidationResult("NoopFontValidator", "PASSED",
                List.of(), List.of(), "TestFont", "Regular", 400, "normal",
                true, true, true, true, true, true, true, true);
        FontAsset asset = new FontAsset("font-001", "test.ttf", "TestFont", "Regular", "ttf",
                1024, "abc123", "s3://fonts/test.ttf", FontAssetStatus.READY, secResult, valResult, null);
        assetRepository.save(asset);
        FontPreflightResult result = preflight.preflight(createJob("font-001", true, "experiment"));
        assertTrue(result.passed());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("not production-safe")));
    }

    @Test
    void missingGlyphsNoFallbackFailsPreflight() {
        FontAsset asset = createAsset("font-001", FontAssetStatus.READY, true);
        assetRepository.save(asset);
        MissingGlyphDetector detector = new MissingGlyphDetector() {
            @Override
            public String detectorName() { return "StubDetector"; }
            @Override
            public List<MissingGlyph> detectMissingGlyphs(String fontId, Set<Integer> requiredCodePoints) {
                return List.of(new MissingGlyph(0x4E2D, "中", "Han", false));
            }
        };
        DefaultRenderJobFontPreflight strictPreflight = new DefaultRenderJobFontPreflight(
                assetRepository, manifestResolver, detector, noopStackResolver);
        FontPreflightResult result = strictPreflight.preflight(createJob("font-001", false, "production"));
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Missing glyphs")));
    }

    @Test
    void missingGlyphsAllowsFallback() {
        FontAsset asset = createAsset("font-001", FontAssetStatus.READY, true);
        assetRepository.save(asset);
        MissingGlyphDetector detector = new MissingGlyphDetector() {
            @Override
            public String detectorName() { return "StubDetector"; }
            @Override
            public List<MissingGlyph> detectMissingGlyphs(String fontId, Set<Integer> requiredCodePoints) {
                return List.of(new MissingGlyph(0x4E2D, "中", "Han", false));
            }
        };
        DefaultRenderJobFontPreflight fallbackPreflight = new DefaultRenderJobFontPreflight(
                assetRepository, manifestResolver, detector, noopStackResolver);
        FontPreflightResult result = fallbackPreflight.preflight(createJob("font-001", true, "production"));
        assertTrue(result.passed());
        assertTrue(result.fallbackUsed());
    }

    @Test
    void readyWithSubsetsReturnsSubsetUrl() {
        FontSubsetResult subsetResult = new FontSubsetResult("pyftsubset", true,
                "cache-key-123", "s3://fonts/subsets/font-001.woff2", "woff2",
                512000, 65536, 1024, List.of(), Map.of());
        FontAsset asset = new FontAsset("font-001", "test.ttf", "TestFont", "Regular", "ttf",
                1024, "abc123", "s3://fonts/test.ttf", FontAssetStatus.READY_WITH_SUBSETS,
                FontSecurityResult.passed("BasicFontSecurityScanner", "abc123", "font/ttf"),
                null, subsetResult);
        assetRepository.save(asset);
        FontPreflightResult result = preflight.preflight(createJob("font-001", true, "production"));
        assertTrue(result.passed());
        assertTrue(result.subsetRequired());
        assertTrue(result.subsetUrls().contains("s3://fonts/subsets/font-001.woff2"));
    }

    @Test
    void cannotBypassManifestResolver() {
        FontAsset asset = createAsset("font-001", FontAssetStatus.READY, true);
        assetRepository.save(asset);
        FontPreflightResult result = preflight.preflight(createJob("font-001", true, "production"));
        assertTrue(result.passed());
        assertTrue(result.resolvedFonts().stream()
                .allMatch(f -> f.fontAssetId() != null && f.hash() != null));
    }

    @Test
    void securityScanPassedDoesNotDirectlyMakeReady() {
        FontSecurityResult secResult = FontSecurityResult.passed("BasicFontSecurityScanner", "abc123", "font/ttf");
        FontAsset asset = new FontAsset("font-001", "test.ttf", "TestFont", "Regular", "ttf",
                1024, "abc123", "s3://fonts/test.ttf", FontAssetStatus.SECURITY_CHECK_PENDING,
                secResult, null, null);
        assetRepository.save(asset);
        FontPreflightResult result = preflight.preflight(createJob("font-001", true, "production"));
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("not ready")));
    }
}
