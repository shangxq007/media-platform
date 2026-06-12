package com.example.platform.render.infrastructure.font;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Font;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Basic missing glyph detector using java.awt.Font.
 *
 * <p>Replaces {@link NoopMissingGlyphDetector} as the production default.
 * Uses AWT font rendering to detect missing glyphs for ASCII, CJK, and emoji ranges.
 *
 * <p>Limitations:
 * <ul>
 *   <li>Emoji detection is best-effort (color emoji not rendered by AWT)</li>
 *   <li>RTL shaping not performed — only codepoint presence checked</li>
 *   <li>Headless environment may limit font loading</li>
 * </ul>
 */
@Component
public class BasicMissingGlyphDetector implements MissingGlyphDetector {

    private static final Logger log = LoggerFactory.getLogger(BasicMissingGlyphDetector.class);

    @Value("${app.fonts.dir:/tmp/platform/fonts}")
    private String fontsDir;

    @Override
    public String detectorName() {
        return "BasicMissingGlyphDetector";
    }

    @Override
    public List<MissingGlyph> detectMissingGlyphs(String fontId, Set<Integer> requiredCodePoints) {
        List<MissingGlyph> missing = new ArrayList<>();

        if (requiredCodePoints == null || requiredCodePoints.isEmpty()) {
            return missing;
        }

        // Try to load the font file
        Font awtFont = loadFont(fontId);
        if (awtFont == null) {
            log.warn("Could not load font {}, reporting all codepoints as potentially missing", fontId);
            // Return warning-style missing glyphs for all requested
            for (int cp : requiredCodePoints) {
                missing.add(new MissingGlyph(cp, new String(Character.toChars(cp)), "unknown", false));
            }
            return missing;
        }

        // Check each required codepoint
        for (int codePoint : requiredCodePoints) {
            String text = new String(Character.toChars(codePoint));

            // Special handling for emoji — AWT can't render color emoji
            if (isEmoji(codePoint)) {
                missing.add(new MissingGlyph(codePoint, text, "emoji", false));
                continue;
            }

            // Use canDisplayUpTo to check if the font can render this character
            int canDisplay = awtFont.canDisplayUpTo(text);
            if (canDisplay >= 0 && canDisplay < text.length()) {
                String script = detectScript(codePoint);
                missing.add(new MissingGlyph(codePoint, text, script, false));
            }
        }

        if (!missing.isEmpty()) {
            log.info("Font {} missing {} of {} required glyphs", fontId, missing.size(), requiredCodePoints.size());
        }

        return missing;
    }

    private Font loadFont(String fontId) {
        // Try TTF first, then OTF
        for (String ext : List.of(".ttf", ".otf")) {
            Path fontPath = Path.of(fontsDir, fontId + ext);
            if (Files.exists(fontPath)) {
                try {
                    return Font.createFont(Font.TRUETYPE_FONT, fontPath.toFile());
                } catch (Exception e) {
                    log.debug("Could not load font {}{}: {}", fontId, ext, e.getMessage());
                }
            }
        }
        return null;
    }

    private boolean isEmoji(int codePoint) {
        return (codePoint >= 0x1F300 && codePoint <= 0x1F5FF)
                || (codePoint >= 0x1F600 && codePoint <= 0x1F64F)
                || (codePoint >= 0x1F680 && codePoint <= 0x1F6FF)
                || (codePoint >= 0x2600 && codePoint <= 0x26FF)
                || (codePoint >= 0x2700 && codePoint <= 0x27BF);
    }

    private String detectScript(int codePoint) {
        if (codePoint >= 0x4E00 && codePoint <= 0x9FFF) return "CJK";
        if (codePoint >= 0x0600 && codePoint <= 0x06FF) return "Arabic";
        if (codePoint >= 0x0590 && codePoint <= 0x05FF) return "Hebrew";
        if (codePoint >= 0x0400 && codePoint <= 0x04FF) return "Cyrillic";
        if (codePoint >= 0x0370 && codePoint <= 0x03FF) return "Greek";
        return "Latin";
    }
}
