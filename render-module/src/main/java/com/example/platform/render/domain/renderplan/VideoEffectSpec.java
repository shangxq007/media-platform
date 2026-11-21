package com.example.platform.render.domain.renderplan;

import com.example.platform.timeline.semantics.effect.BasicVideoEffect;
import java.util.Objects;

/** Provider-neutral physical projection of BasicVideoEffect. */
public record VideoEffectSpec(
        double brightness,
        double contrast,
        double saturation,
        boolean grayscale,
        double fadeInSeconds,
        double fadeOutSeconds,
        boolean enabled) {

    public static final VideoEffectSpec IDENTITY = new VideoEffectSpec(0, 1, 1, false, 0, 0, true);

    public VideoEffectSpec {
        if (Double.isNaN(brightness) || Double.isInfinite(brightness)
                || Double.isNaN(contrast) || Double.isInfinite(contrast)
                || Double.isNaN(saturation) || Double.isInfinite(saturation)
                || Double.isNaN(fadeInSeconds) || Double.isInfinite(fadeInSeconds)
                || Double.isNaN(fadeOutSeconds) || Double.isInfinite(fadeOutSeconds)) {
            throw new IllegalArgumentException("video effect values must be finite");
        }
        if (brightness < -1 || brightness > 1 || contrast < 0 || contrast > 2
                || saturation < 0 || saturation > 2 || fadeInSeconds < 0 || fadeOutSeconds < 0) {
            throw new IllegalArgumentException("video effect values out of range");
        }
    }

    public static VideoEffectSpec from(BasicVideoEffect effect) {
        Objects.requireNonNull(effect, "effect");
        return new VideoEffectSpec(effect.brightness(), effect.contrast(), effect.saturation(),
                effect.grayscale(), effect.fadeInSeconds(), effect.fadeOutSeconds(), effect.enabled());
    }
}
