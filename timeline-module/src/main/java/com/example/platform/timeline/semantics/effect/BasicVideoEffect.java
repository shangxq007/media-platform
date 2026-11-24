package com.example.platform.timeline.semantics.effect;

import java.util.Map;
import java.util.Set;

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
    public static final String FORMAT = "rgb-basic-v1";
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

    /** The stored video.basic R1 operation parameters. */
    public static BasicVideoEffect fromParameters(Map<String, Object> parameters) {
        if (!parameters.keySet().equals(Set.of("format", "brightness", "contrast", "saturation", "grayscale",
                "fadeInSeconds", "fadeOutSeconds", "enabled"))
                || !FORMAT.equals(parameters.get("format"))
                || !(parameters.get("brightness") instanceof Number brightness)
                || !(parameters.get("contrast") instanceof Number contrast)
                || !(parameters.get("saturation") instanceof Number saturation)
                || !(parameters.get("grayscale") instanceof Boolean grayscale)
                || !(parameters.get("fadeInSeconds") instanceof Number fadeIn)
                || !(parameters.get("fadeOutSeconds") instanceof Number fadeOut)
                || !(parameters.get("enabled") instanceof Boolean enabled)) {
            throw new IllegalArgumentException("Invalid video.basic R1 parameters");
        }
        return new BasicVideoEffect(brightness.doubleValue(), contrast.doubleValue(), saturation.doubleValue(),
                grayscale, fadeIn.doubleValue(), fadeOut.doubleValue(), enabled);
    }

    public Map<String, Object> toParameters() {
        return Map.of("format", FORMAT, "brightness", brightness, "contrast", contrast,
                "saturation", saturation, "grayscale", grayscale, "fadeInSeconds", fadeInSeconds,
                "fadeOutSeconds", fadeOutSeconds, "enabled", enabled);
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
