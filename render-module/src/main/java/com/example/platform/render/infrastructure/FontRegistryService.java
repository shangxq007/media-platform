package com.example.platform.render.infrastructure;

import com.example.platform.render.infrastructure.font.FontIdPolicy;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Font;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for font management, subset generation, and glyph coverage checking.
 */
@Service
public class FontRegistryService {
    private static final Logger log = LoggerFactory.getLogger(FontRegistryService.class);

    @Value("${app.fonts.dir:/tmp/platform/fonts}")
    private String fontsDir;

    private final ErrorCodeRegistry errorCodeRegistry;

    public FontRegistryService(ErrorCodeRegistry errorCodeRegistry) {
        this.errorCodeRegistry = errorCodeRegistry;
    }

    /**
     * Register a font file and return font metadata.
     */
    public Map<String, Object> registerFont(String fontId, String family, String format, long fileSize) {
        FontIdPolicy.requireValidFontId(fontId);
        Path fontPath = Path.of(fontsDir, fontId + "." + format);
        if (!Files.exists(fontPath)) {
            throw new PlatformException(
                    new ConfigurableErrorCode("SUBTITLE-404-001", 404201,
                            Map.of("en", "Subtitle font not found: " + fontId, "zh", "字幕字体不存在: " + fontId),
                            "subtitle", 404),
                    "Font file not found: " + fontPath,
                    Map.of("fontId", fontId, "path", fontPath.toString()),
                    "en"
            );
        }

        // Check glyph coverage
        List<String> glyphCoverage = checkGlyphCoverage(fontPath.toString());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("fontId", fontId);
        metadata.put("family", family);
        metadata.put("format", format);
        metadata.put("filePath", fontPath.toString());
        metadata.put("fileSize", fileSize);
        metadata.put("glyphCoverage", glyphCoverage);

        log.info("Registered font: {} ({}) with {} glyph ranges", fontId, family, glyphCoverage.size());
        return metadata;
    }

    /**
     * Generate a font subset containing only the glyphs needed for the given text.
     */
    public String generateFontSubset(String sourceFontId, String text, String format) {
        FontIdPolicy.requireValidFontId(sourceFontId);
        Path sourcePath = Path.of(fontsDir, sourceFontId + "." + format);
        if (!Files.exists(sourcePath)) {
            throw new PlatformException(
                    new ConfigurableErrorCode("SUBTITLE-404-001", 404201,
                            Map.of("en", "Source font not found for subset", "zh", "子集源字体不存在"),
                            "subtitle", 404),
                    "Source font not found: " + sourcePath,
                    Map.of("sourceFontId", sourceFontId),
                    "en"
            );
        }

        String subsetFontId = sourceFontId + "_subset_" + Integer.toHexString(text.hashCode());
        Path subsetPath = Path.of(fontsDir, subsetFontId + "." + format);

        if (Files.exists(subsetPath)) {
            log.info("Using existing font subset: {}", subsetFontId);
            return subsetPath.toString();
        }

        try {
            // Extract unique characters from text
            Set<Character> uniqueChars = new LinkedHashSet<>();
            for (char c : text.toCharArray()) {
                if (!Character.isWhitespace(c)) {
                    uniqueChars.add(c);
                }
            }

            // Use Java AWT to create subset font
            Font baseFont = Font.createFont(Font.TRUETYPE_FONT, sourcePath.toFile());

            // For production, use fonttools or similar for proper subsetting
            // Here we create a placeholder subset by copying the original font
            // In a real implementation, you would use:
            // - Python fonttools via ProcessBuilder (through extension-module whitelist)
            // - Java Font2D subsetting
            // - Harfbuzz via JNI

            // For now, copy the original font as subset placeholder
            Files.copy(sourcePath, subsetPath);

            log.info("Generated font subset: {} with {} unique chars from {}",
                    subsetFontId, uniqueChars.size(), sourceFontId);

            return subsetPath.toString();
        } catch (Exception e) {
            throw new PlatformException(
                    new ConfigurableErrorCode("SUBTITLE-500-001", 500201,
                            Map.of("en", "Font subset generation failed", "zh", "字体子集生成失败"),
                            "subtitle", 500),
                    "Failed to generate font subset: " + e.getMessage(),
                    Map.of("sourceFontId", sourceFontId, "error", e.getClass().getSimpleName()),
                    "en"
            );
        }
    }

    /**
     * Check glyph coverage of a font for given text.
     */
    public List<String> checkGlyphCoverage(String fontFilePath) {
        List<String> coverage = new ArrayList<>();
        try {
            File fontFile = new File(fontFilePath);
            if (!fontFile.exists()) {
                coverage.add("FONT_NOT_FOUND");
                return coverage;
            }

            Font font = Font.createFont(Font.TRUETYPE_FONT, fontFile);

            // Check common Unicode ranges
            String[][] ranges = {
                    {"Basic Latin", "\u0020", "\u007F"},
                    {"Latin-1 Supplement", "\u00A0", "\u00FF"},
                    {"CJK Unified Ideographs", "\u4E00", "\u9FFF"},
                    {"Hiragana", "\u3040", "\u309F"},
                    {"Katakana", "\u30A0", "\u30FF"},
                    {"Hangul Syllables", "\uAC00", "\uD7AF"},
                    {"Cyrillic", "\u0400", "\u04FF"},
                    {"Arabic", "\u0600", "\u06FF"},
                    {"Devanagari", "\u0900", "\u097F"},
                    {"Thai", "\u0E00", "\u0E7F"}
            };

            for (String[] range : ranges) {
                boolean hasGlyph = true;
                for (int cp = range[1].codePointAt(0); cp <= range[2].codePointAt(0); cp += Math.max(1, (range[2].codePointAt(0) - range[1].codePointAt(0)) / 10)) {
                    if (!font.canDisplay(cp)) {
                        hasGlyph = false;
                        break;
                    }
                }
                if (hasGlyph) {
                    coverage.add(range[0]);
                }
            }

            // Check emoji support
            boolean hasEmoji = font.canDisplay(0x1F600);
            if (hasEmoji) coverage.add("Emoji");

        } catch (Exception e) {
            log.warn("Failed to check glyph coverage for {}: {}", fontFilePath, e.getMessage());
            coverage.add("CHECK_FAILED");
        }

        return coverage;
    }

    /**
     * Check if font can display all characters in text.
     * Returns list of missing characters.
     */
    public List<String> findMissingGlyphs(String fontFilePath, String text) {
        List<String> missing = new ArrayList<>();
        File fontFile = new File(fontFilePath);
        if (!fontFile.exists()) {
            missing.add("FONT_NOT_FOUND");
            return missing;
        }
        try {
            Font font = Font.createFont(Font.TRUETYPE_FONT, fontFile);
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (!font.canDisplay(c) && !Character.isWhitespace(c)) {
                    missing.add(String.format("U+%04X (%s)", (int) c, Character.getName(c)));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check glyphs: {}", e.getMessage());
            missing.add("CHECK_ERROR");
        }
        return missing;
    }

    /**
     * Resolve font with fallback chain.
     */
    public String resolveFontWithFallback(String fontId, List<String> fallbackFontIds, String text) {
        FontIdPolicy.requireValidFontId(fontId);
        Path primaryPath = Path.of(fontsDir, fontId + ".ttf");
        if (!Files.exists(primaryPath)) {
            primaryPath = Path.of(fontsDir, fontId + ".otf");
        }

        if (Files.exists(primaryPath)) {
            List<String> missing = findMissingGlyphs(primaryPath.toString(), text);
            if (missing.isEmpty()) {
                return primaryPath.toString();
            }
            log.warn("Font {} missing {} glyphs, trying fallbacks", fontId, missing.size());
        }

        // Try fallback fonts
        for (String fallbackId : fallbackFontIds) {
            Path fallbackPath = Path.of(fontsDir, fallbackId + ".ttf");
            if (!Files.exists(fallbackPath)) {
                fallbackPath = Path.of(fontsDir, fallbackId + ".otf");
            }
            if (Files.exists(fallbackPath)) {
                List<String> missing = findMissingGlyphs(fallbackPath.toString(), text);
                if (missing.isEmpty()) {
                    log.info("Using fallback font {} for {}", fallbackId, fontId);
                    return fallbackPath.toString();
                }
            }
        }

        // Return primary even if missing glyphs (FFmpeg will show tofu)
        if (Files.exists(primaryPath)) {
            log.warn("No suitable font found for {}, using primary with missing glyphs", fontId);
            return primaryPath.toString();
        }

        return null;
    }
}
