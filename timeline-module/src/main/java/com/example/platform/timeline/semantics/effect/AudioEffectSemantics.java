package com.example.platform.timeline.semantics.effect;

import java.util.List;
import java.util.Objects;

/**
 * Audio effect semantics: Gain, Pan, Fade, SimpleEqualizer, Compressor, Limiter.
 * <p>
 * Units:
 * - gain: dB or linear multiplier
 * - pan: [-1 (left) .. +1 (right)]
 * - frequency: Hz
 * - Q / bandwidth: dimensionless
 * - threshold: dB
 * - attack / release: milliseconds
 * <p>
 * Audio tail behavior is explicit:
 * - CLIP: audio is clipped at clip boundaries
 * - MIX_WITHIN_TIMELINE: audio fades within timeline
 * - EXTEND_TIMELINE: audio extends beyond clip (v1 default: PROHIBITED)
 */
public final class AudioEffectSemantics {

    private AudioEffectSemantics() {}

    /**
     * Gain effect: amplitude scaling.
     */
    public record GainEffect(double gainDb) {
        public GainEffect {
            if (gainDb < -96) throw new IllegalArgumentException("gainDb must be >= -96");
            if (gainDb > 24) throw new IllegalArgumentException("gainDb must be <= 24");
        }

        public static GainEffect unity() {
            return new GainEffect(0);
        }

        /**
         * Converts dB gain to linear multiplier.
         */
        public double toLinear() {
            return Math.pow(10, gainDb / 20);
        }
    }

    /**
     * Pan effect: stereo panning.
     */
    public record PanEffect(double pan) {
        public PanEffect {
            if (pan < -1 || pan > 1) throw new IllegalArgumentException("pan must be in [-1, 1]");
        }

        public static PanEffect center() {
            return new PanEffect(0);
        }

        public static PanEffect fullLeft() {
            return new PanEffect(-1);
        }

        public static PanEffect fullRight() {
            return new PanEffect(1);
        }
    }

    /**
     * Audio fade effect.
     */
    public record AudioFadeEffect(
        FadeType type,
        double startGainDb,
        double endGainDb
    ) {
        public AudioFadeEffect {
            if (startGainDb < -96 || startGainDb > 24)
                throw new IllegalArgumentException("startGainDb must be in [-96, 24]");
            if (endGainDb < -96 || endGainDb > 24)
                throw new IllegalArgumentException("endGainDb must be in [-96, 24]");
        }

        public enum FadeType {
            FADE_IN, FADE_OUT
        }

        public static AudioFadeEffect fadeIn() {
            return new AudioFadeEffect(FadeType.FADE_IN, -96, 0);
        }

        public static AudioFadeEffect fadeOut() {
            return new AudioFadeEffect(FadeType.FADE_OUT, 0, -96);
        }
    }

    /**
     * Simple equalizer band.
     */
    public record EqBand(
        double frequencyHz,
        double gainDb,
        double q
    ) {
        public EqBand {
            if (frequencyHz < 20 || frequencyHz > 20000)
                throw new IllegalArgumentException("frequencyHz must be in [20, 20000]");
            if (gainDb < -12 || gainDb > 12)
                throw new IllegalArgumentException("gainDb must be in [-12, 12]");
            if (q <= 0 || q > 10)
                throw new IllegalArgumentException("q must be in (0, 10]");
        }
    }

    /**
     * Simple equalizer effect.
     */
    public record SimpleEqualizerEffect(List<EqBand> bands) {
        public SimpleEqualizerEffect {
            Objects.requireNonNull(bands, "bands");
            if (bands.size() > 10) throw new IllegalArgumentException("Max 10 EQ bands");
            bands = List.copyOf(bands);
        }

        public static SimpleEqualizerEffect flat() {
            return new SimpleEqualizerEffect(List.of());
        }
    }

    /**
     * Compressor effect.
     */
    public record CompressorEffect(
        double thresholdDb,
        double ratio,
        double attackMs,
        double releaseMs,
        double kneeDb,
        double makeupGainDb
    ) {
        public CompressorEffect {
            if (thresholdDb > 0) throw new IllegalArgumentException("thresholdDb must be <= 0");
            if (ratio < 1 || ratio > 20) throw new IllegalArgumentException("ratio must be in [1, 20]");
            if (attackMs < 0.01 || attackMs > 500)
                throw new IllegalArgumentException("attackMs must be in [0.01, 500]");
            if (releaseMs < 1 || releaseMs > 3000)
                throw new IllegalArgumentException("releaseMs must be in [1, 3000]");
            if (kneeDb < 0 || kneeDb > 12)
                throw new IllegalArgumentException("kneeDb must be in [0, 12]");
            if (makeupGainDb < 0 || makeupGainDb > 24)
                throw new IllegalArgumentException("makeupGainDb must be in [0, 24]");
        }

        public static CompressorEffect defaultCompressor() {
            return new CompressorEffect(-24, 4, 5, 50, 3, 6);
        }
    }

    /**
     * Limiter effect (brick-wall).
     */
    public record LimiterEffect(
        double thresholdDb,
        double releaseMs
    ) {
        public LimiterEffect {
            if (thresholdDb > 0) throw new IllegalArgumentException("thresholdDb must be <= 0");
            if (releaseMs < 1 || releaseMs > 1000)
                throw new IllegalArgumentException("releaseMs must be in [1, 1000]");
        }

        public static LimiterEffect defaultLimiter() {
            return new LimiterEffect(-1, 50);
        }
    }

    /**
     * Channel behavior for audio effects.
     */
    public enum ChannelBehavior {
        MONO,
        STEREO,
        SURROUND_5_1,
        SURROUND_7_1,
        PASS_THROUGH
    }

    /**
     * Audio tail handling.
     */
    public enum AudioTailMode {
        /** Clip audio at clip boundaries (no extension). */
        CLIP,
        /** Mix with other audio in the timeline region. */
        MIX_WITHIN_TIMELINE,
        /** Extend timeline to accommodate audio tail (v1: NOT default). */
        EXTEND_TIMELINE
    }

    /**
     * Canonical serialization keys for audio effects.
     */
    public static final class FieldKeys {
        public static final String GAIN = "gain";
        public static final String PAN = "pan";
        public static final String AUDIO_FADE = "audioFade";
        public static final String EQUALIZER = "equalizer";
        public static final String COMPRESSOR = "compressor";
        public static final String LIMITER = "limiter";

        private FieldKeys() {}
    }
}
