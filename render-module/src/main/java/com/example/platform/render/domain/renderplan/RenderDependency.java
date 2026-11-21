package com.example.platform.render.domain.renderplan;

import com.example.platform.audio.domain.mix.AudioMixInput;

/**
 * Typed render dependency variants (C5). Sealed — no universal untyped bag.
 * Each edge carries exactly one variant.
 */
public sealed interface RenderDependency permits
        RenderDependency.DecodedFrames,
        RenderDependency.EffectInput,
        RenderDependency.AudioInput,
        RenderDependency.SubtitleRaster,
        RenderDependency.CompositeInput,
        RenderDependency.TimedCompositeInput,
        RenderDependency.TimedTrackInput {

    /**
     * Canonical variant key for deterministic edge ordering.
     */
    String variantKey();

    /** Consumer depends on a DECODE node's decoded media output. */
    record DecodedFrames() implements RenderDependency {
        @Override
        public String variantKey() {
            return "DECODED_FRAMES";
        }
    }

    /** Consumer depends on an EFFECT node's processed output. */
    record EffectInput() implements RenderDependency {
        @Override
        public String variantKey() {
            return "EFFECT_INPUT";
        }
    }

    /** Audio consumer depends on a clip audio source/process/mix node. */
    record AudioInput(AudioMixInput mixInput) implements RenderDependency {
        public AudioInput {
            if (mixInput == null) {
                throw new IllegalArgumentException("AudioInput mixInput required");
            }
        }

        @Override
        public String variantKey() {
            return "AUDIO_INPUT:" + mixInput;
        }
    }

    /**
     * TIMED_TEXT consumer depends on a future raster-input node (reserved;
     * unused in this slice).
     */
    record SubtitleRaster() implements RenderDependency {
        @Override
        public String variantKey() {
            return "SUBTITLE_RASTER";
        }
    }

    /**
     * ROADMAP20 correction F2: COMPOSITE/OUTPUT consumer depends on the typed
     * visual composition input (video producer and/or TIMED_TEXT raster output).
     */
    record CompositeInput() implements RenderDependency {
        @Override
        public String variantKey() {
            return "COMPOSITE_INPUT";
        }
    }

    /** A video producer's exact visible interval in the requested Timeline. */
    record TimedCompositeInput(com.example.platform.shared.time.MediaTime start,
                               com.example.platform.shared.time.MediaTime end)
            implements RenderDependency {
        public TimedCompositeInput {
            java.util.Objects.requireNonNull(start, "start");
            java.util.Objects.requireNonNull(end, "end");
            if (!start.isLessThan(end)) throw new IllegalArgumentException("positive composite interval required");
        }
        @Override public String variantKey() {
            return "TIMED_COMPOSITE_INPUT:" + start + ":" + end;
        }
    }
    /**
     * Exact placement of a decoded media clip and its order in the verified Timeline.
     * For audio inputs the canonical AudioRoute gain/mute/balance is carried inline,
     * so the physical provider receives the authored mix values without a second
     * audio authority. Video inputs use neutral defaults.
     */
    record TimedTrackInput(com.example.platform.shared.time.MediaTime start,
                           com.example.platform.shared.time.MediaTime end, int layer, boolean audio,
                           com.example.platform.audio.domain.mix.AudioGain gain,
                           com.example.platform.audio.domain.mix.AudioMute mute,
                           com.example.platform.audio.domain.mix.StereoBalance balance,
                           VideoTransformSpec transform,
                           VideoEffectSpec effect,
                           com.example.platform.shared.time.MediaTime transitionStart,
                           com.example.platform.shared.time.MediaTime transitionDuration)
            implements RenderDependency {
        public TimedTrackInput {
            java.util.Objects.requireNonNull(start, "start");
            java.util.Objects.requireNonNull(end, "end");
            if (gain == null) gain = com.example.platform.audio.domain.mix.AudioGain.defaultGain();
            if (mute == null) mute = com.example.platform.audio.domain.mix.AudioMute.defaultMute();
            if (balance == null) balance = com.example.platform.audio.domain.mix.StereoBalance.neutral();
            if (transform == null) transform = VideoTransformSpec.IDENTITY;
            if (effect == null) effect = VideoEffectSpec.IDENTITY;
            if (transitionStart == null) transitionStart = start;
            if (transitionDuration == null) transitionDuration = com.example.platform.shared.time.MediaTime.ZERO;
            if (!start.isLessThan(end) || layer < 0) {
                throw new IllegalArgumentException("positive track interval and nonnegative layer required");
            }
        }

        /** Compatibility constructor: neutral mix values and identity transform. */
        public TimedTrackInput(com.example.platform.shared.time.MediaTime start,
                               com.example.platform.shared.time.MediaTime end, int layer, boolean audio,
                               com.example.platform.audio.domain.mix.AudioGain gain,
                               com.example.platform.audio.domain.mix.AudioMute mute,
                               com.example.platform.audio.domain.mix.StereoBalance balance) {
            this(start, end, layer, audio, gain, mute, balance, VideoTransformSpec.IDENTITY);
        }

        /** Compatibility constructor: identity effect/transition with explicit transform. */
        public TimedTrackInput(com.example.platform.shared.time.MediaTime start,
                               com.example.platform.shared.time.MediaTime end, int layer, boolean audio,
                               com.example.platform.audio.domain.mix.AudioGain gain,
                               com.example.platform.audio.domain.mix.AudioMute mute,
                               com.example.platform.audio.domain.mix.StereoBalance balance,
                               VideoTransformSpec transform) {
            this(start, end, layer, audio, gain, mute, balance, transform,
                    VideoEffectSpec.IDENTITY, start, com.example.platform.shared.time.MediaTime.ZERO);
        }

        /** Compatibility constructor: neutral mix values. */
        public TimedTrackInput(com.example.platform.shared.time.MediaTime start,
                               com.example.platform.shared.time.MediaTime end, int layer, boolean audio) {
            this(start, end, layer, audio,
                    com.example.platform.audio.domain.mix.AudioGain.defaultGain(),
                    com.example.platform.audio.domain.mix.AudioMute.defaultMute(),
                    com.example.platform.audio.domain.mix.StereoBalance.neutral(),
                    VideoTransformSpec.IDENTITY, VideoEffectSpec.IDENTITY, start,
                    com.example.platform.shared.time.MediaTime.ZERO);
        }

        public double gainLinear() { return gain.linear(); }
        public boolean muted() { return mute.muted(); }
        public double balanceValue() { return balance.value(); }

        @Override public String variantKey() {
            var t = transform;
            return "TIMED_TRACK_INPUT:" + start + ":" + end + ":" + layer + ":" + audio
                    + ":" + gainLinear() + ":" + muted() + ":" + balanceValue()
                    + ":" + t.positionX() + ":" + t.positionY() + ":" + t.scaleX() + ":" + t.scaleY()
                    + ":" + t.rotationDegrees() + ":" + t.cropLeft() + ":" + t.cropRight()
                    + ":" + t.cropTop() + ":" + t.cropBottom() + ":" + t.opacity()
                    + ":" + effect.brightness() + ":" + effect.contrast() + ":" + effect.saturation()
                    + ":" + effect.grayscale() + ":" + effect.fadeInSeconds() + ":"
                    + effect.fadeOutSeconds() + ":" + effect.enabled()
                    + ":" + transitionStart + ":" + transitionDuration;
        }
    }
}
