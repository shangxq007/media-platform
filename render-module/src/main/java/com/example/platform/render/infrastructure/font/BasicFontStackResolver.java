package com.example.platform.render.infrastructure.font;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Basic font stack resolver that provides deterministic fallback behavior.
 *
 * <p>Replaces {@link NoopFontStackResolver} as the production default.
 * Does not perform real shaping (HarfBuzz) but provides safe defaults
 * for subtitle burn-in and text overlay.
 */
@Component
public class BasicFontStackResolver implements FontStackResolver {

    private static final Logger log = LoggerFactory.getLogger(BasicFontStackResolver.class);

    private static final String DEFAULT_LATIN_FALLBACK = "DejaVu Sans";
    private static final String DEFAULT_CJK_FALLBACK = "Noto Sans CJK SC";
    private static final String DEFAULT_EMOJI_FALLBACK = "Noto Color Emoji";
    private static final String SYSTEM_FALLBACK = "sans-serif";

    // Unicode ranges for script detection
    private static final int CJK_START = 0x4E00;
    private static final int CJK_END = 0x9FFF;
    private static final int CJK_EXT_A_START = 0x3400;
    private static final int CJK_EXT_A_END = 0x4DBF;
    private static final int EMOJI_START = 0x1F600;
    private static final int EMOJI_END = 0x1F64F;
    private static final int EMOJI_EXT_START = 0x1F300;
    private static final int EMOJI_EXT_END = 0x1F5FF;
    private static final int RTL_ARABIC_START = 0x0600;
    private static final int RTL_ARABIC_END = 0x06FF;
    private static final int RTL_HEBREW_START = 0x0590;
    private static final int RTL_HEBREW_END = 0x05FF;

    @Override
    public String resolverName() {
        return "BasicFontStackResolver";
    }

    @Override
    public FontStack resolve(String fontFamily, Map<String, FontAsset> availableFonts) {
        List<String> fallbacks = new ArrayList<>();

        // If a specific font family is requested and available, use it
        if (fontFamily != null && !fontFamily.isBlank()) {
            boolean found = availableFonts.values().stream()
                    .anyMatch(f -> fontFamily.equalsIgnoreCase(f.fontFamily()));
            if (found) {
                fallbacks.add(fontFamily);
            } else {
                log.debug("Requested font '{}' not found in available fonts, using fallbacks", fontFamily);
            }
        }

        // Add default Latin fallback
        fallbacks.add(DEFAULT_LATIN_FALLBACK);

        return new FontStack(
                fontFamily != null ? fontFamily : DEFAULT_LATIN_FALLBACK,
                fallbacks,
                SYSTEM_FALLBACK
        );
    }

    @Override
    public FallbackChain resolveChain(String fontId, Set<Integer> requiredCodePoints,
            Map<String, FontAsset> availableFonts) {
        List<String> fallbackIds = new ArrayList<>();
        boolean systemFallbackUsed = false;

        // Detect required scripts
        boolean needsCjk = requiredCodePoints.stream().anyMatch(this::isCjk);
        boolean needsEmoji = requiredCodePoints.stream().anyMatch(this::isEmoji);
        boolean needsRtl = requiredCodePoints.stream().anyMatch(this::isRtl);

        if (needsRtl) {
            log.warn("RTL text detected but HarfBuzz shaping not available — rendering may be incorrect");
        }

        // Primary font
        if (fontId != null) {
            fallbackIds.add(fontId);
        }

        // CJK fallback
        if (needsCjk) {
            boolean hasCjkFont = availableFonts.values().stream()
                    .anyMatch(f -> isCjkFont(f.fontFamily()));
            if (hasCjkFont) {
                availableFonts.entrySet().stream()
                        .filter(e -> isCjkFont(e.getValue().fontFamily()))
                        .findFirst()
                        .ifPresent(e -> fallbackIds.add(e.getKey()));
            } else {
                fallbackIds.add(DEFAULT_CJK_FALLBACK);
                log.debug("No CJK font available, adding default CJK fallback");
            }
        }

        // Emoji fallback
        if (needsEmoji) {
            fallbackIds.add(DEFAULT_EMOJI_FALLBACK);
            log.debug("Emoji detected, adding emoji fallback");
        }

        // System fallback
        systemFallbackUsed = true;

        return new FallbackChain(
                fontId != null ? fontId : DEFAULT_LATIN_FALLBACK,
                fallbackIds,
                systemFallbackUsed
        );
    }

    private boolean isCjk(int codePoint) {
        return (codePoint >= CJK_START && codePoint <= CJK_END)
                || (codePoint >= CJK_EXT_A_START && codePoint <= CJK_EXT_A_END);
    }

    private boolean isEmoji(int codePoint) {
        return (codePoint >= EMOJI_START && codePoint <= EMOJI_END)
                || (codePoint >= EMOJI_EXT_START && codePoint <= EMOJI_EXT_END);
    }

    private boolean isRtl(int codePoint) {
        return (codePoint >= RTL_ARABIC_START && codePoint <= RTL_ARABIC_END)
                || (codePoint >= RTL_HEBREW_START && codePoint <= RTL_HEBREW_END);
    }

    private boolean isCjkFont(String family) {
        if (family == null) return false;
        String lower = family.toLowerCase();
        return lower.contains("cjk") || lower.contains("noto sans")
                || lower.contains("source han") || lower.contains("wenquanyi");
    }
}
