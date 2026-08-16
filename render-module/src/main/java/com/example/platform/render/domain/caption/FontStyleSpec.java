package com.example.platform.render.domain.caption;

/**
 * Font style specification for captions.
 * Internal domain model.
 *
 * @param family      font family name (e.g., "DejaVu Sans")
 * @param weight      font weight (e.g., 400=normal, 700=bold)
 * @param color       text color hex (e.g., "#FFFFFF")
 * @param outlineColor outline color hex (e.g., "#000000")
 * @param outlineWidth outline width in pixels (0-10)
 * @param backgroundColor background color hex (null = transparent)
 */
public record FontStyleSpec(
        String family,
        int weight,
        String color,
        String outlineColor,
        int outlineWidth,
        String backgroundColor) {

    /**
     * ROADMAP_19 FINAL TIMELINE AUTHORITY CANONICALIZATION:
     * NO invented font family. Family is intentionally {@code null} so any
     * consumer without an explicit font selection fails closed instead of
     * silently inventing a platform font.
     */
    public static FontStyleSpec defaults() {
        return new FontStyleSpec(null, 400, "#FFFFFF", "#000000", 2, null);
    }
}
