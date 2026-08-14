package com.example.platform.audio.domain.mix;

/**
 * AUDIO_V2 (A6): canonical stereo balance (pan), bounded to {@code [-1, 1]}.
 *
 * <p>Semantics: {@code -1} = full left, {@code 0} = center (neutral),
 * {@code 1} = full right. Finite only; NaN/Infinity rejected. Multichannel
 * positioning, 3D/spatial audio, ambisonics and immersive audio object graphs
 * are DEFERRED beyond this milestone.
 */
public record StereoBalance(double value) {

    public static final double MIN = -1.0;
    public static final double MAX = 1.0;
    public static final double NEUTRAL = 0.0;

    public StereoBalance {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("StereoBalance must be finite: " + value);
        }
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException("StereoBalance must be within [" + MIN + ", " + MAX + "]: " + value);
        }
    }

    public static StereoBalance of(double value) {
        return new StereoBalance(value);
    }

    public static StereoBalance neutral() {
        return new StereoBalance(NEUTRAL);
    }

    public static StereoBalance fullLeft() {
        return new StereoBalance(MIN);
    }

    public static StereoBalance fullRight() {
        return new StereoBalance(MAX);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
