package com.example.platform.render.domain.timeline;

/**
 * Vector/raster sticker overlay (L6 Skia-compatible path).
 */
public record TimelineSticker(
        String id,
        String imageUri,
        double x,
        double y,
        double width,
        double height,
        double startTime,
        double duration,
        double opacity) {

    public static TimelineSticker of(String id, String imageUri, double x, double y,
                                     double width, double height, double start, double duration) {
        return new TimelineSticker(id, imageUri, x, y, width, height, start, duration, 1.0);
    }
}
