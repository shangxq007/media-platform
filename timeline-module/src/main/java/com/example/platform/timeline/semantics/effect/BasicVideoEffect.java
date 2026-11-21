package com.example.platform.timeline.semantics.effect;

/**
 * Canonical clip-local basic video effect (R1).
 * Defaults are identity and do not alter an existing clip.
 */
public record BasicVideoEffect(
        double brightness,
        double contrast,
        double saturation,
        boolean grayscale,
        double fadeInSeconds,
        double fadeOutSeconds,
        boolean enabled) {

    public static final String EFFECT_KEY = "video.basic";
    public static final BasicVideoEffect IDENTITY = new BasicVideoEffect(0, 1, 1, false, 0, 0, true);

    public BasicVideoEffect {
        requireFinite(brightness, "brightness");
        requireFinite(contrast, "contrast");
        requireFinite(saturation, "saturation");
        requireFinite(fadeInSeconds, "fadeInSeconds");
        requireFinite(fadeOutSeconds, "fadeOutSeconds");
        if (brightness < -1 || brightness > 1) {
            throw new IllegalArgumentException("brightness must be in [-1,1]");
        }
        if (contrast < 0 || contrast > 2) {
            throw new IllegalArgumentException("contrast must be in [0,2]");
        }
        if (saturation < 0 || saturation > 2) {
            throw new IllegalArgumentException("saturation must be in [0,2]");
        }
        if (fadeInSeconds < 0 || fadeOutSeconds < 0) {
            throw new IllegalArgumentException("fade durations must be >= 0");
        }
    }

    private static void requireFinite(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    public boolean identity() {
        return brightness == 0 && contrast == 1 && saturation == 1
                && !grayscale && fadeInSeconds == 0 && fadeOutSeconds == 0;
    }
}
