package com.example.platform.render.infrastructure.font;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FontAssetStatusTest {

    @Test
    void uploadedCannotGoDirectlyToReady() {
        FontAsset asset = new FontAsset("font-001", "test.ttf", "Test", "Regular", "ttf",
                1024, "abc123", "s3://fonts/test.ttf",
                FontAssetStatus.UPLOADED, null, null, null);
        assertFalse(asset.isReadyForRender());
    }

    @Test
    void quarantinedCannotBeUsedForRender() {
        FontAsset asset = new FontAsset("font-001", "test.ttf", "Test", "Regular", "ttf",
                1024, "abc123", "s3://fonts/test.ttf",
                FontAssetStatus.QUARANTINED, null, null, null);
        assertFalse(asset.isReadyForRender());
    }

    @Test
    void readyAssetCanBeUsedForRender() {
        FontAsset asset = new FontAsset("font-001", "test.ttf", "Test", "Regular", "ttf",
                1024, "abc123", "s3://fonts/test.ttf",
                FontAssetStatus.READY,
                FontSecurityResult.passed("BasicFontSecurityScanner", "abc123", "font/ttf"),
                null, null);
        assertTrue(asset.isReadyForRender());
        assertTrue(asset.isProductionSafe());
    }

    @Test
    void readyWithSubsetsCanBeUsedForRender() {
        FontAsset asset = new FontAsset("font-001", "test.ttf", "Test", "Regular", "ttf",
                1024, "abc123", "s3://fonts/test.ttf",
                FontAssetStatus.READY_WITH_SUBSETS,
                FontSecurityResult.passed("BasicFontSecurityScanner", "abc123", "font/ttf"),
                null, null);
        assertTrue(asset.isReadyForRender());
        assertTrue(asset.isProductionSafe());
    }

    @Test
    void securityRejectedCannotBeProductionSafe() {
        FontSecurityResult rejected = FontSecurityResult.rejected("BasicFontSecurityScanner",
                List.of("Blocked file extension"));
        FontAsset asset = new FontAsset("font-001", "test.ttf", "Test", "Regular", "ttf",
                1024, null, "s3://fonts/test.ttf",
                FontAssetStatus.SECURITY_REJECTED, rejected, null, null);
        assertFalse(asset.isProductionSafe());
        assertFalse(asset.isReadyForRender());
    }

    @Test
    void validationFailedCannotBeReady() {
        FontAsset asset = new FontAsset("font-001", "test.ttf", "Test", "Regular", "ttf",
                1024, "abc123", "s3://fonts/test.ttf",
                FontAssetStatus.VALIDATION_FAILED,
                FontSecurityResult.passed("BasicFontSecurityScanner", "abc123", "font/ttf"),
                null, null);
        assertFalse(asset.isReadyForRender());
        assertFalse(asset.isProductionSafe());
    }

    @Test
    void disabledCannotBeUsed() {
        FontAsset asset = new FontAsset("font-001", "test.ttf", "Test", "Regular", "ttf",
                1024, "abc123", "s3://fonts/test.ttf",
                FontAssetStatus.DISABLED,
                FontSecurityResult.passed("BasicFontSecurityScanner", "abc123", "font/ttf"),
                null, null);
        assertFalse(asset.isReadyForRender());
        assertFalse(asset.isProductionSafe());
    }

    @Test
    void missingGlyphsCanBeRecorded() {
        MissingGlyph missing = new MissingGlyph(0x4E2D, "中", "Han", false);
        FontSubsetResult subsetResult = new FontSubsetResult("pyftsubset", true,
                "cache-key-123", "s3://fonts/subsets/font-001.woff2",
                "woff2", 512000, 65536, 1024,
                List.of(missing), java.util.Map.of());

        FontAsset asset = new FontAsset("font-001", "test.ttf", "Test", "Regular", "ttf",
                1024, "abc123", "s3://fonts/test.ttf",
                FontAssetStatus.READY_WITH_SUBSETS,
                FontSecurityResult.passed("BasicFontSecurityScanner", "abc123", "font/ttf"),
                null, subsetResult);

        assertTrue(asset.subsetResult().hasMissingGlyphs());
        assertEquals(1, asset.subsetResult().missingGlyphs().size());
        assertEquals("中", asset.subsetResult().missingGlyphs().getFirst().character());
    }
}
