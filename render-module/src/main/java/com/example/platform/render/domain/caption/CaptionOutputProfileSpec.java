package com.example.platform.render.domain.caption;

/**
 * Output profile for caption template render.
 * Internal domain model.
 *
 * @param width       output width (e.g., 1920)
 * @param height      output height (e.g., 1080)
 * @param fps         frame rate (e.g., 30)
 * @param container   container format (e.g., "mp4")
 */
public record CaptionOutputProfileSpec(
        int width,
        int height,
        double fps,
        String container) {

    public static CaptionOutputProfileSpec hd1080p() {
        return new CaptionOutputProfileSpec(1920, 1080, 30.0, "mp4");
    }

    public static CaptionOutputProfileSpec hd720p() {
        return new CaptionOutputProfileSpec(1280, 720, 30.0, "mp4");
    }
}
