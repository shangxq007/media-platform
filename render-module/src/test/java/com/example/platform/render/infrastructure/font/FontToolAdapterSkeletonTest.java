package com.example.platform.render.infrastructure.font;

import com.example.platform.render.infrastructure.font.FontMetadata;
import com.example.platform.render.infrastructure.font.FontMetadataExtractor;
import com.example.platform.render.infrastructure.font.FontSubsetter;
import com.example.platform.render.infrastructure.font.FontValidator;
import com.example.platform.render.infrastructure.font.FontCoverageChecker;
import com.example.platform.render.infrastructure.font.FontToolsMetadataExtractor;
import com.example.platform.render.infrastructure.font.PyftsubsetFontSubsetter;
import com.example.platform.render.infrastructure.font.FontBakeryValidator;
import com.example.platform.render.infrastructure.font.HarfBuzzShapingValidator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FontToolAdapterSkeletonTest {

    @Test
    void fontToolsMetadataExtractorDisabled() {
        FontToolsMetadataExtractor extractor = new FontToolsMetadataExtractor();
        assertFalse(extractor.enabled());
        assertEquals("FontToolsMetadataExtractor", extractor.extractorName());
    }

    @Test
    void pyftsubsetFontSubsetterDisabled() {
        PyftsubsetFontSubsetter subsetter = new PyftsubsetFontSubsetter();
        assertFalse(subsetter.enabled());
        assertEquals("PyftsubsetFontSubsetter", subsetter.subsetterName());
    }

    @Test
    void fontBakeryValidatorDisabled() {
        FontBakeryValidator validator = new FontBakeryValidator();
        assertFalse(validator.enabled());
        assertEquals("FontBakeryValidator", validator.validatorName());
    }

    @Test
    void harfBuzzShapingValidatorDisabled() {
        HarfBuzzShapingValidator checker = new HarfBuzzShapingValidator();
        assertFalse(checker.enabled());
        assertEquals("HarfBuzzShapingValidator", checker.checkerName());
    }

    @Test
    void fontToolsReturnsEmptyMetadataWhenDisabled() {
        FontToolsMetadataExtractor extractor = new FontToolsMetadataExtractor();
        FontMetadata metadata = extractor.extract(Path.of("/tmp/test.ttf"));
        assertNotNull(metadata);
        assertFalse(metadata.hasCmap());
        assertFalse(metadata.hasGlyf());
        assertNull(metadata.sha256());
    }

    @Test
    void pyftsubsetReturnsEmptyResultWhenDisabled() {
        PyftsubsetFontSubsetter subsetter = new PyftsubsetFontSubsetter();
        FontSubsetResult result = subsetter.subset(Path.of("/tmp/test.ttf"),
                Set.of(0x41, 0x42), FontSubsetter.SubsetOptions.defaultWoff2());
        assertNull(result.cacheKey());
        assertEquals("pyftsubset", result.strategy());
    }

    @Test
    void fontBakeryReturnsDisabledStatusWhenDisabled() {
        FontBakeryValidator validator = new FontBakeryValidator();
        FontValidationResult result = validator.validate(Path.of("/tmp/test.ttf"));
        assertEquals("DISABLED", result.validationStatus());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("disabled")));
    }

    @Test
    void harfBuzzReturnsEmptyCoverageWhenDisabled() {
        HarfBuzzShapingValidator checker = new HarfBuzzShapingValidator();
        FontCoverageChecker.CoverageResult result = checker.checkCoverage(Set.of(0x41), Set.of("Latin"));
        assertTrue(result.supportedCodePoints().isEmpty());
        assertEquals(Set.of(0x41), result.missingCodePoints());
    }
}
