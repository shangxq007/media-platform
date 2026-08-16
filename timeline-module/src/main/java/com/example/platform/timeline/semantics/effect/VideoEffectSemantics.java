package com.example.platform.timeline.semantics.effect;

import java.util.List;
import java.util.Objects;

/**
 * Video effect semantics: Transform, Crop, Opacity, BlendMode, ColorAdjustment,
 * GaussianBlur, Fade.
 * <p>
 * These are semantic contracts only — no backend rendering is performed.
 * Each parameter has a strong type, valid range, default value, canonical
 * serialization, and version.
 */
public final class VideoEffectSemantics {

    private VideoEffectSemantics() {}

    // ===== Transform =====

    /**
     * Transform effect: position, scale, rotation, anchor point.
     */
    public record TransformEffect(
        double translateX,
        double translateY,
        double scaleX,
        double scaleY,
        double rotationDegrees,
        double anchorX,
        double anchorY
    ) {
        public TransformEffect {
            if (scaleX == 0 || scaleY == 0) {
                throw new IllegalArgumentException("Scale must not be zero");
            }
        }

        public static TransformEffect identity() {
            return new TransformEffect(0, 0, 1, 1, 0, 0.5, 0.5);
        }
    }

    // ===== Crop =====

    /**
     * Crop effect: rectangular crop with values as ratios (0..1).
     * Parameter order: (left, right, top, bottom).
     */
    public record CropEffect(
        double leftRatio,
        double rightRatio,
        double topRatio,
        double bottomRatio
    ) {
        public CropEffect {
            if (leftRatio < 0 || leftRatio > 1) throw new IllegalArgumentException("leftRatio must be in [0,1]");
            if (rightRatio < 0 || rightRatio > 1) throw new IllegalArgumentException("rightRatio must be in [0,1]");
            if (topRatio < 0 || topRatio > 1) throw new IllegalArgumentException("topRatio must be in [0,1]");
            if (bottomRatio < 0 || bottomRatio > 1) throw new IllegalArgumentException("bottomRatio must be in [0,1]");
            if (leftRatio >= rightRatio) throw new IllegalArgumentException("leftRatio must be < rightRatio");
            if (topRatio >= bottomRatio) throw new IllegalArgumentException("topRatio must be < bottomRatio");
        }

        public static CropEffect none() {
            return new CropEffect(0, 1, 0, 1);
        }
    }

    // ===== Opacity =====

    /**
     * Opacity effect: alpha multiplier.
     */
    public record OpacityEffect(double opacity) {
        public OpacityEffect {
            if (opacity < 0 || opacity > 1) {
                throw new IllegalArgumentException("opacity must be in [0,1]");
            }
        }

        public static OpacityEffect full() {
            return new OpacityEffect(1.0);
        }

        public static OpacityEffect invisible() {
            return new OpacityEffect(0.0);
        }
    }

    // ===== Blend Mode =====

    /**
     * Finite blend mode enumeration. No arbitrary backend strings allowed.
     */
    public enum BlendMode {
        NORMAL("normal"),
        MULTIPLY("multiply"),
        SCREEN("screen"),
        OVERLAY("overlay"),
        DARKEN("darken"),
        LIGHTEN("lighten"),
        COLOR_DODGE("color_dodge"),
        COLOR_BURN("color_burn"),
        HARD_LIGHT("hard_light"),
        SOFT_LIGHT("soft_light"),
        DIFFERENCE("difference"),
        EXCLUSION("exclusion"),
        HUE("hue"),
        SATURATION("saturation"),
        COLOR("color"),
        LUMINOSITY("luminosity"),
        ADD("add"),
        SUBTRACT("subtract");

        private final String canonicalName;

        BlendMode(String canonicalName) {
            this.canonicalName = canonicalName;
        }

        public String canonicalName() {
            return canonicalName;
        }

        public static BlendMode fromCanonical(String name) {
            Objects.requireNonNull(name, "name");
            for (BlendMode mode : values()) {
                if (mode.canonicalName.equals(name)) return mode;
            }
            throw new IllegalArgumentException("Unknown BlendMode: " + name);
        }
    }

    // ===== Color Adjustment =====

    /**
     * Color adjustment effect: brightness, contrast, saturation, hue rotation.
     */
    public record ColorAdjustmentEffect(
        double brightness,
        double contrast,
        double saturation,
        double hueRotationDegrees
    ) {
        public ColorAdjustmentEffect {
            if (brightness < -1 || brightness > 1)
                throw new IllegalArgumentException("brightness must be in [-1,1]");
            if (contrast < 0 || contrast > 2)
                throw new IllegalArgumentException("contrast must be in [0,2]");
            if (saturation < 0 || saturation > 2)
                throw new IllegalArgumentException("saturation must be in [0,2]");
            if (hueRotationDegrees < -180 || hueRotationDegrees > 180)
                throw new IllegalArgumentException("hueRotationDegrees must be in [-180,180]");
        }

        public static ColorAdjustmentEffect identity() {
            return new ColorAdjustmentEffect(0, 1, 1, 0);
        }
    }

    // ===== Gaussian Blur =====

    /**
     * Gaussian blur effect.
     */
    public record GaussianBlurEffect(double radiusPixels) {
        public GaussianBlurEffect {
            if (radiusPixels < 0) throw new IllegalArgumentException("radiusPixels must be >= 0");
        }

        public static GaussianBlurEffect none() {
            return new GaussianBlurEffect(0);
        }
    }

    // ===== Fade (video) =====

    /**
     * Video fade: fade from/to black over a time range.
     */
    public record VideoFadeEffect(
        FadeType type,
        double startOpacity,
        double endOpacity
    ) {
        public VideoFadeEffect {
            if (startOpacity < 0 || startOpacity > 1)
                throw new IllegalArgumentException("startOpacity must be in [0,1]");
            if (endOpacity < 0 || endOpacity > 1)
                throw new IllegalArgumentException("endOpacity must be in [0,1]");
        }

        public enum FadeType {
            FADE_IN, FADE_OUT, FADE_IN_OUT
        }

        public static VideoFadeEffect fadeIn() {
            return new VideoFadeEffect(FadeType.FADE_IN, 0, 1);
        }

        public static VideoFadeEffect fadeOut() {
            return new VideoFadeEffect(FadeType.FADE_OUT, 1, 0);
        }
    }

    /**
     * Canonical serialization keys for video effects.
     */
    public static final class FieldKeys {
        public static final String TRANSFORM = "transform";
        public static final String CROP = "crop";
        public static final String OPACITY = "opacity";
        public static final String BLEND_MODE = "blendMode";
        public static final String COLOR_ADJUSTMENT = "colorAdjustment";
        public static final String GAUSSIAN_BLUR = "gaussianBlur";
        public static final String VIDEO_FADE = "videoFade";

        private FieldKeys() {}
    }
}
