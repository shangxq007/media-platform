package com.example.platform.render.domain.caption;

/**
 * Typed ASS subtitle style parameters — safe, bounded output for libass.
 *
 * <p>Maps from {@link CaptionStyleSpec} / {@link FontStyleSpec} domain models
 * to ASS v4+ style fields. All fields are bounded and validated.</p>
 *
 * <p>This record is the typed intent that produces safe ASS style output.
 * It does NOT contain raw ASS style strings.</p>
 *
 * @param fontFamily      ASS FontName (from allowed set)
 * @param fontSize        ASS Fontsize (8-200)
 * @param bold            ASS Bold (0 or 1)
 * @param primaryColor    ASS PrimaryColour as &amp;HAABBGGRR (unsigned int)
 * @param outlineColor    ASS OutlineColour as &amp;HAABBGGRR (unsigned int)
 * @param outlineWidth    ASS Outline (0-10)
 * @param alignment       ASS Alignment (1-9, numpad layout)
 * @param marginL         ASS MarginL (0-200)
 * @param marginR         ASS MarginR (0-200)
 * @param marginV         ASS MarginV (0-200)
 */
public record AssStyleParams(
        String fontFamily,
        int fontSize,
        int bold,
        long primaryColor,
        long outlineColor,
        int outlineWidth,
        int alignment,
        int marginL,
        int marginR,
        int marginV
) {

    /**
     * Safe bounds for validation.
     */
    public static final int MIN_FONT_SIZE = 8;
    public static final int MAX_FONT_SIZE = 200;
    public static final int MAX_OUTLINE_WIDTH = 10;
    public static final int MIN_ALIGNMENT = 1;
    public static final int MAX_ALIGNMENT = 9;
    public static final int MAX_MARGIN = 200;

    /**
     * Returns true if all fields are within safe bounds.
     */
    public boolean isWithinBounds() {
        return fontSize >= MIN_FONT_SIZE && fontSize <= MAX_FONT_SIZE
                && (bold == 0 || bold == 1)
                && outlineWidth >= 0 && outlineWidth <= MAX_OUTLINE_WIDTH
                && alignment >= MIN_ALIGNMENT && alignment <= MAX_ALIGNMENT
                && marginL >= 0 && marginL <= MAX_MARGIN
                && marginR >= 0 && marginR <= MAX_MARGIN
                && marginV >= 0 && marginV <= MAX_MARGIN
                && primaryColor >= 0 && primaryColor <= 0xFFFFFFFFL
                && outlineColor >= 0 && outlineColor <= 0xFFFFFFFFL;
    }
}
