package com.example.platform.render.domain.caption;

/**
 * Caption style specification — layout and visual style.
 * Internal domain model.
 *
 * @param placement   screen placement
 * @param font        font style
 * @param fontSize    font size in points (8-200)
 * @param maxLines    maximum lines (1-10)
 * @param lineHeight  line height multiplier (0.5-3.0)
 * @param textAlign   text alignment (left/center/right)
 */
public record CaptionStyleSpec(
        CaptionPlacement placement,
        FontStyleSpec font,
        int fontSize,
        int maxLines,
        double lineHeight,
        String textAlign) {

    public static CaptionStyleSpec defaults() {
        return new CaptionStyleSpec(
                CaptionPlacement.BOTTOM_CENTER,
                FontStyleSpec.defaults(),
                24, 2, 1.4, "center");
    }
}
