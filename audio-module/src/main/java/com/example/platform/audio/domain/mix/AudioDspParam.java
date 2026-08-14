package com.example.platform.audio.domain.mix;

import java.util.Objects;

/**
 * AUDIO_V2 (A9): bounded DSP parameter.
 *
 * <p>A named, finite numeric parameter. NaN/Infinity rejected. Provider-neutral:
 * the name identifies the canonical semantic parameter (e.g. {@code threshold},
 * {@code ratio}), never an FFmpeg filter argument.
 */
public record AudioDspParam(
        String name,
        double value) {

    public AudioDspParam {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("AudioDspParam name must not be blank");
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("AudioDspParam must be finite: " + name);
        }
    }

    public static AudioDspParam of(String name, double value) {
        return new AudioDspParam(name, value);
    }

    @Override
    public String toString() {
        return name + "=" + value;
    }
}
