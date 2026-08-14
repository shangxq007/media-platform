package com.example.platform.audio.domain.mix;

import java.util.Objects;

/**
 * AUDIO_V2 (A4): canonical linear gain.
 *
 * <p>Invariants: finite, {@code >= 0}, default {@code 1.0}. NaN, Infinity and
 * negative values are rejected at construction. Gain is semantically distinct
 * from {@link AudioMute}: {@code gain = 0, mute = false} is a canonical
 * user/mix choice of zero gain; {@code gain = 1, mute = true} is a mute
 * semantic. A provider may compile both to the same DSP output, but the
 * canonical revision content must distinguish them.
 */
public record AudioGain(double linear) {

    public static final double DEFAULT_LINEAR = 1.0;

    public AudioGain {
        if (Double.isNaN(linear) || Double.isInfinite(linear)) {
            throw new IllegalArgumentException("AudioGain must be finite: " + linear);
        }
        if (linear < 0.0) {
            throw new IllegalArgumentException("AudioGain must be >= 0: " + linear);
        }
    }

    public static AudioGain of(double linear) {
        return new AudioGain(linear);
    }

    public static AudioGain defaultGain() {
        return new AudioGain(DEFAULT_LINEAR);
    }

    /**
     * Deterministic canonical representation (linear gain, finite, no
     * platform-incidental formatting): {@code "1.0"}, {@code "0.5"}.
     */
    @Override
    public String toString() {
        return String.valueOf(linear);
    }
}
