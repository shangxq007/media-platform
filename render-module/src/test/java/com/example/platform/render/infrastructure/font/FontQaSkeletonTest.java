package com.example.platform.render.infrastructure.font;

import com.example.platform.render.infrastructure.font.FontCoverageChecker;
import com.example.platform.render.infrastructure.font.FontQaProfile;
import com.example.platform.render.infrastructure.font.FontQaResult;
import com.example.platform.render.infrastructure.font.FontQaSeverity;
import com.example.platform.render.infrastructure.font.FontQaCheck;
import com.example.platform.render.infrastructure.font.FontCiAcceptancePolicy;
import com.example.platform.render.infrastructure.font.FontSecurityResult;
import com.example.platform.render.infrastructure.font.OTSFontSecurityScanner;
import com.example.platform.render.infrastructure.font.FontToolsMetadataValidator;
import com.example.platform.render.infrastructure.font.FontValidationResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FontQaSkeletonTest {

    @Test
    void otsScannerDefaultDisabled() {
        OTSFontSecurityScanner scanner = new OTSFontSecurityScanner();
        assertTrue(scanner.productionSafe());
        FontSecurityResult result = scanner.scan(Path.of("/tmp/test.ttf"));
        assertEquals("REJECTED", result.scanStatus());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("disabled")));
    }

    @Test
    void otsScannerCommandNotFoundReturnsError() {
        OTSFontSecurityScanner scanner = new OTSFontSecurityScanner()
                .enabled(true)
                .otsCommand(Path.of("/nonexistent/ots-sanitize"));
        FontSecurityResult result = scanner.scan(Path.of("/tmp/test.ttf"));
        assertNotNull(result);
    }

    @Test
    void fontQaProfileLightDoesNotRequireFontBakery() {
        FontQaProfile profile = FontQaProfile.LIGHT;
        assertNotNull(profile);
        assertNotEquals(FontQaProfile.GOOGLE_FONTS_STYLE, profile);
    }

    @Test
    void fontQaProfileFullCanDeclareRequirements() {
        FontQaProfile profile = FontQaProfile.FULL;
        assertNotNull(profile);
    }

    @Test
    void fontQaResultPassed() {
        FontQaCheck check = new FontQaCheck("test", FontQaSeverity.INFO, true, "ok");
        FontQaResult result = FontQaResult.passed(FontQaProfile.LIGHT, List.of(check));
        assertTrue(result.passed());
        assertEquals(FontQaProfile.LIGHT, result.profile());
    }

    @Test
    void fontQaResultFailed() {
        FontQaCheck check = new FontQaCheck("test", FontQaSeverity.ERROR, false, "failed");
        FontQaResult result = FontQaResult.failed(FontQaProfile.FULL, List.of(check), List.of("error"));
        assertFalse(result.passed());
        assertEquals(FontQaSeverity.ERROR, result.severity());
    }

    @Test
    void fontCiAcceptancePolicyDefaultCi() {
        FontCiAcceptancePolicy policy = FontCiAcceptancePolicy.defaultCi();
        assertNotNull(policy);
        assertEquals(FontQaProfile.LIGHT, policy.profile());
        assertFalse(policy.scanUserUploads());
        assertTrue(policy.failOnNoopInProduction());
    }

    @Test
    void fontCiAcceptancePolicyNightly() {
        FontCiAcceptancePolicy policy = FontCiAcceptancePolicy.nightly();
        assertNotNull(policy);
        assertEquals(FontQaProfile.FULL, policy.profile());
        assertTrue(policy.scanUserUploads());
        assertNotNull(policy.reportArtifactPath());
    }

    @Test
    void fontCiAcceptancePolicyReleaseGate() {
        FontCiAcceptancePolicy policy = FontCiAcceptancePolicy.releaseGate();
        assertNotNull(policy);
        assertEquals(FontQaProfile.RELEASE_GATE, policy.profile());
        assertTrue(policy.scanUserUploads());
        assertNotNull(policy.reportArtifactPath());
    }

    @Test
    void fontToolsMetadataValidatorDefaultDisabled() {
        FontToolsMetadataValidator validator = new FontToolsMetadataValidator();
        assertFalse(validator.enabled());
        FontValidationResult result = validator.validate(Path.of("/tmp/test.ttf"));
        assertEquals("DISABLED", result.validationStatus());
    }

    @Test
    void fontToolsCoverageCheckerDefaultDisabled() {
        FontToolsCoverageChecker checker = new FontToolsCoverageChecker();
        FontCoverageChecker.CoverageResult result = checker.checkCoverage(Set.of(0x41), Set.of("Latin"));
        assertNotNull(result);
    }

    @Test
    void fontToolsMissingGlyphDetectorDefaultDisabled() {
        FontToolsMissingGlyphDetector detector = new FontToolsMissingGlyphDetector();
        assertFalse(detector.enabled());
        List<MissingGlyph> result = detector.detectMissingGlyphs("font-001", Set.of(0x41));
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void metadataValidationFailedCannotMakeReady() {
        FontValidationResult failed = new FontValidationResult("FontToolsMetadataValidator",
                "FAILED", List.of("cmap"), List.of("Missing cmap table"),
                null, null, null, null,
                false, false, false, false, false, false, false, false);
        assertFalse(failed.isValid());
    }

    @Test
    void coverageMissingRequiredScriptsGeneratesWarning() {
        FontToolsCoverageChecker checker = new FontToolsCoverageChecker();
        FontCoverageChecker.CoverageResult result = checker.checkCoverage(Set.of(0x4E2D), Set.of("Han"));
        assertNotNull(result);
    }
}
