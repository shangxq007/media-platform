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
        new BasicVideoEffect(brightness, contrast, saturation, grayscale,
                fadeInSeconds, fadeOutSeconds, enabled);
    }

    public static VideoEffectSpec from(BasicVideoEffect effect) {
        Objects.requireNonNull(effect, "effect");
        return new VideoEffectSpec(effect.brightness(), effect.contrast(), effect.saturation(),
                effect.grayscale(), effect.fadeInSeconds(), effect.fadeOutSeconds(), effect.enabled());
    }
}
