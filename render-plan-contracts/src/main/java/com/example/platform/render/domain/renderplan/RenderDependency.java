package com.example.platform.render.domain.renderplan;

import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.timeline.semantics.effect.PositionKeys;
import com.example.platform.timeline.semantics.effect.ScaleKeys;
import java.util.Optional;

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
    /** An incoming layer's authored linear dissolve and its explicit outgoing clip. */
    record Dissolve(String transitionId, String outgoingClipId, String incomingClipId,
                    com.example.platform.shared.time.MediaTime duration) {
        public Dissolve {
            java.util.Objects.requireNonNull(transitionId);
            java.util.Objects.requireNonNull(outgoingClipId);
            java.util.Objects.requireNonNull(incomingClipId);
            java.util.Objects.requireNonNull(duration);
            if (transitionId.isBlank() || outgoingClipId.isBlank() || incomingClipId.isBlank()
                    || outgoingClipId.equals(incomingClipId) || duration.ticks() <= 0) {
                throw new IllegalArgumentException("Invalid dissolve identity or duration");
            }
        }
    }

    /**
     * Exact placement of a decoded media clip and its order in the verified Timeline.
     * For audio inputs the canonical AudioRoute gain/mute/balance is carried inline,
     * so the physical provider receives the authored mix values without a second
     * audio authority. Video inputs use neutral defaults.
     */
    /** Execution projection of verified opacity samples. Times use the same domain as the track placement. */
    record OpacityRamp(com.example.platform.shared.time.MediaTime firstTime, double firstValue,
                       com.example.platform.shared.time.MediaTime lastTime, double lastValue) {
        public OpacityRamp {
            if (!firstTime.isLessThan(lastTime) || !(firstValue >= 0 && firstValue <= 1 && lastValue >= 0 && lastValue <= 1))
                throw new IllegalArgumentException("Invalid opacity ramp");
        }
        public OpacityRamp relativeTo(com.example.platform.shared.time.MediaTime origin) {
            return new OpacityRamp(firstTime.subtract(origin), firstValue, lastTime.subtract(origin), lastValue);
        }
    }

    /** Typed execution projection of a verified position animation. */
    record PositionRamp(PositionKeys keys) {
        public PositionRamp {
            if (keys.keys().isEmpty()) throw new IllegalArgumentException("Position ramp must have samples");
        }

        public PositionRamp relativeTo(com.example.platform.shared.time.MediaTime origin) {
            var samples = keys.keys().stream().map(k -> new PositionKeys.Key(
                    k.time().subtract(origin), k.xMicros(), k.yMicros())).toList();
            return new PositionRamp(new PositionKeys(samples));
        }

        public String canonicalSamples() {
            return keys.canonicalSamples();
        }

        public boolean endsWithin(java.math.BigDecimal durationSeconds) {
            var end = keys.keys().getLast().time();
            var seconds = java.math.BigDecimal.valueOf(end.ticks()).divide(java.math.BigDecimal.valueOf(end.timeScale()),
                    9, java.math.RoundingMode.HALF_UP);
            return seconds.compareTo(durationSeconds) <= 0;
        }

        public static PositionRamp parseSamples(String samples) {
            return new PositionRamp(PositionKeys.parseSamples(samples));
        }
    }

    /** Typed graph-side copy of the uniform scale channel. Providers do not depend on Timeline authoring. */
    record ScaleRamp(ScaleKeys keys) {
        public ScaleRamp {
            if (keys.keys().isEmpty()) throw new IllegalArgumentException("Scale ramp cannot be empty");
        }

        public ScaleRamp relativeTo(com.example.platform.shared.time.MediaTime origin) {
            return new ScaleRamp(keys.relativeTo(origin));
        }

        public String canonicalSamples() { return keys.canonicalSamples(); }

        public boolean changesScale() {
            return keys.keys().stream().anyMatch(k -> k.multiplierMicros() != 1_000_000);
        }

        public void validateRelative(com.example.platform.shared.time.MediaTime duration, int sx, int sy) {
            keys.requireInside(com.example.platform.shared.time.MediaTime.ZERO, duration);
            keys.requireBounds(sx, sy);
        }

        public static ScaleRamp parseSamples(String samples) { return new ScaleRamp(ScaleKeys.parseSamples(samples)); }
    }

    record VideoPlacement(double x, double y, double scaleX, double scaleY, double degrees,
            double left, double right, double top, double bottom, double opacity) {}

    record TimedTrackInput(com.example.platform.shared.time.MediaTime start,
                           com.example.platform.shared.time.MediaTime end, int layer, boolean audio,
                           com.example.platform.audio.domain.mix.AudioGain gain,
                           com.example.platform.audio.domain.mix.AudioMute mute,
                           com.example.platform.audio.domain.mix.StereoBalance balance,
                           com.example.platform.timeline.semantics.effect.VideoTransform transform,
                           VideoEffectSpec effect,
                           com.example.platform.shared.time.MediaTime authoredStart,
                           com.example.platform.shared.time.MediaTime authoredEnd,
                           java.util.Optional<Dissolve> dissolve, java.util.Optional<OpacityRamp> opacityKeys,
                           Optional<PositionRamp> positionKeys, Optional<ScaleRamp> scaleKeys)
            implements RenderDependency {
        public TimedTrackInput(com.example.platform.shared.time.MediaTime start,
                           com.example.platform.shared.time.MediaTime end, int layer, boolean audio,
                           com.example.platform.audio.domain.mix.AudioGain gain,
                           com.example.platform.audio.domain.mix.AudioMute mute,
                           com.example.platform.audio.domain.mix.StereoBalance balance,
                           com.example.platform.timeline.semantics.effect.VideoTransform transform,
                           VideoEffectSpec effect,
                           com.example.platform.shared.time.MediaTime authoredStart,
                           com.example.platform.shared.time.MediaTime authoredEnd,
                           java.util.Optional<Dissolve> dissolve) {
            this(start, end, layer, audio, gain, mute, balance, transform, effect, authoredStart, authoredEnd, dissolve, java.util.Optional.empty());
        }

        public TimedTrackInput(com.example.platform.shared.time.MediaTime start,
                com.example.platform.shared.time.MediaTime end, int layer, boolean audio,
                com.example.platform.audio.domain.mix.AudioGain gain, com.example.platform.audio.domain.mix.AudioMute mute,
                com.example.platform.audio.domain.mix.StereoBalance balance,
                com.example.platform.timeline.semantics.effect.VideoTransform transform, VideoEffectSpec effect,
                com.example.platform.shared.time.MediaTime authoredStart, com.example.platform.shared.time.MediaTime authoredEnd,
                Optional<Dissolve> dissolve, Optional<OpacityRamp> opacityKeys) {
            this(start, end, layer, audio, gain, mute, balance, transform, effect, authoredStart, authoredEnd,
                    dissolve, opacityKeys, Optional.empty());
        }

        public TimedTrackInput(com.example.platform.shared.time.MediaTime start,
                com.example.platform.shared.time.MediaTime end, int layer, boolean audio,
                com.example.platform.audio.domain.mix.AudioGain gain, com.example.platform.audio.domain.mix.AudioMute mute,
                com.example.platform.audio.domain.mix.StereoBalance balance,
                com.example.platform.timeline.semantics.effect.VideoTransform transform, VideoEffectSpec effect,
                com.example.platform.shared.time.MediaTime authoredStart, com.example.platform.shared.time.MediaTime authoredEnd,
                Optional<Dissolve> dissolve, Optional<OpacityRamp> opacityKeys, Optional<PositionRamp> positionKeys) {
            this(start, end, layer, audio, gain, mute, balance, transform, effect, authoredStart, authoredEnd,
                    dissolve, opacityKeys, positionKeys, Optional.empty());
        }

        public TimedTrackInput {
            java.util.Objects.requireNonNull(start, "start");
            java.util.Objects.requireNonNull(end, "end");
            if (gain == null) gain = com.example.platform.audio.domain.mix.AudioGain.defaultGain();
            if (mute == null) mute = com.example.platform.audio.domain.mix.AudioMute.defaultMute();
            if (balance == null) balance = com.example.platform.audio.domain.mix.StereoBalance.neutral();
            java.util.Objects.requireNonNull(transform, "transform");
            if (effect == null) effect = VideoEffectSpec.IDENTITY;
            java.util.Objects.requireNonNull(authoredStart, "authoredStart");
            java.util.Objects.requireNonNull(authoredEnd, "authoredEnd");
            java.util.Objects.requireNonNull(dissolve, "dissolve");
            if (opacityKeys.isPresent() && (opacityKeys.get().firstTime().isLessThan(authoredStart)
                    || !opacityKeys.get().lastTime().isLessThan(authoredEnd))) throw new IllegalArgumentException("Opacity ramp outside clip");
            positionKeys.ifPresent(p -> {
                if (audio) throw new IllegalArgumentException("Position keys require a video placement");
                p.keys().requireInside(authoredStart, authoredEnd);
            });
            scaleKeys.ifPresent(ramp -> {
                if (audio) throw new IllegalArgumentException("Scale channels require video");
                ramp.keys().requireInside(authoredStart, authoredEnd);
                ramp.keys().requireBounds(transform);
            });
            if (audio && opacityKeys.isPresent()) throw new IllegalArgumentException("Audio cannot carry opacity keys");
            if (!start.isLessThan(end) || start.isLessThan(authoredStart) || end.isGreaterThan(authoredEnd)
                    || layer < 0 || (audio && dissolve.isPresent())) {
                throw new IllegalArgumentException("Invalid track placement or dissolve");
            }
            if (dissolve.isPresent() && authoredStart.add(dissolve.get().duration()).isGreaterThan(authoredEnd)) {
                throw new IllegalArgumentException("Dissolve extends beyond the authored clip");
            }
        }

        public static TimedTrackInput video(com.example.platform.shared.time.MediaTime start,
                com.example.platform.shared.time.MediaTime end, int layer, com.example.platform.timeline.semantics.effect.VideoTransform transform,
                VideoEffectSpec effect, com.example.platform.shared.time.MediaTime authoredStart,
                com.example.platform.shared.time.MediaTime authoredEnd, java.util.Optional<Dissolve> dissolve) {
            return video(start, end, layer, transform, effect, authoredStart, authoredEnd, dissolve, java.util.Optional.empty());
        }

        public static TimedTrackInput video(com.example.platform.shared.time.MediaTime start,
                com.example.platform.shared.time.MediaTime end, int layer, com.example.platform.timeline.semantics.effect.VideoTransform transform,
                VideoEffectSpec effect, com.example.platform.shared.time.MediaTime authoredStart,
                com.example.platform.shared.time.MediaTime authoredEnd, java.util.Optional<Dissolve> dissolve, java.util.Optional<OpacityRamp> opacityKeys) {
            return video(start, end, layer, transform, effect, authoredStart, authoredEnd, dissolve, opacityKeys, Optional.empty());
        }

        public static TimedTrackInput video(com.example.platform.shared.time.MediaTime start,
                com.example.platform.shared.time.MediaTime end, int layer, com.example.platform.timeline.semantics.effect.VideoTransform transform,
                VideoEffectSpec effect, com.example.platform.shared.time.MediaTime authoredStart,
                com.example.platform.shared.time.MediaTime authoredEnd, Optional<Dissolve> dissolve,
                Optional<OpacityRamp> opacityKeys, Optional<PositionRamp> positionKeys) {
            return video(start, end, layer, transform, effect, authoredStart, authoredEnd, dissolve, opacityKeys, positionKeys, Optional.empty());
        }

        public static TimedTrackInput video(com.example.platform.shared.time.MediaTime start,
                com.example.platform.shared.time.MediaTime end, int layer, com.example.platform.timeline.semantics.effect.VideoTransform transform,
                VideoEffectSpec effect, com.example.platform.shared.time.MediaTime authoredStart,
                com.example.platform.shared.time.MediaTime authoredEnd, Optional<Dissolve> dissolve,
                Optional<OpacityRamp> opacityKeys, Optional<PositionRamp> positionKeys, Optional<ScaleRamp> scaleKeys) {
            return new TimedTrackInput(start, end, layer, false,
                    com.example.platform.audio.domain.mix.AudioGain.defaultGain(),
                    com.example.platform.audio.domain.mix.AudioMute.defaultMute(),
                    com.example.platform.audio.domain.mix.StereoBalance.neutral(),
                    transform, effect, authoredStart, authoredEnd, dissolve, opacityKeys, positionKeys, scaleKeys);
        }

        public VideoPlacement placement() {
            double unit = com.example.platform.timeline.semantics.effect.VideoTransform.UNIT;
            return new VideoPlacement(transform.positionXMicros() / unit, transform.positionYMicros() / unit,
                    transform.scaleXMicros() / unit, transform.scaleYMicros() / unit,
                    transform.rotationMicroDegrees() / unit, transform.cropLeftMicros() / unit,
                    transform.cropRightMicros() / unit, transform.cropTopMicros() / unit,
                    transform.cropBottomMicros() / unit, transform.opacityMicros() / unit);
        }

        /** Compatibility constructor: neutral mix values and identity transform. */
        public TimedTrackInput(com.example.platform.shared.time.MediaTime start,
                               com.example.platform.shared.time.MediaTime end, int layer, boolean audio,
                               com.example.platform.audio.domain.mix.AudioGain gain,
                               com.example.platform.audio.domain.mix.AudioMute mute,
                               com.example.platform.audio.domain.mix.StereoBalance balance) {
            this(start, end, layer, audio, gain, mute, balance, com.example.platform.timeline.semantics.effect.VideoTransform.IDENTITY);
        }

        /** Compatibility constructor: identity effect/transition with explicit transform. */
        public TimedTrackInput(com.example.platform.shared.time.MediaTime start,
                               com.example.platform.shared.time.MediaTime end, int layer, boolean audio,
                               com.example.platform.audio.domain.mix.AudioGain gain,
                               com.example.platform.audio.domain.mix.AudioMute mute,
                               com.example.platform.audio.domain.mix.StereoBalance balance,
                               com.example.platform.timeline.semantics.effect.VideoTransform transform) {
            this(start, end, layer, audio, gain, mute, balance, transform,
                    VideoEffectSpec.IDENTITY, start, end, java.util.Optional.empty());
        }

        /** Compatibility constructor: neutral mix values. */
        public TimedTrackInput(com.example.platform.shared.time.MediaTime start,
                               com.example.platform.shared.time.MediaTime end, int layer, boolean audio) {
            this(start, end, layer, audio,
                    com.example.platform.audio.domain.mix.AudioGain.defaultGain(),
                    com.example.platform.audio.domain.mix.AudioMute.defaultMute(),
                    com.example.platform.audio.domain.mix.StereoBalance.neutral(),
                    com.example.platform.timeline.semantics.effect.VideoTransform.IDENTITY, VideoEffectSpec.IDENTITY, start, end, java.util.Optional.empty());
        }

        public double gainLinear() { return gain.linear(); }
        public boolean muted() { return mute.muted(); }
        public double balanceValue() { return balance.value(); }

        @Override public String variantKey() {
            var t = transform;
            return "TIMED_TRACK_INPUT:" + start + ":" + end + ":" + layer + ":" + audio
                    + ":" + gainLinear() + ":" + muted() + ":" + balanceValue()
                    + ":" + t.positionXMicros() + ":" + t.positionYMicros() + ":" + t.scaleXMicros() + ":" + t.scaleYMicros()
                    + ":" + t.rotationMicroDegrees() + ":" + t.cropLeftMicros() + ":" + t.cropRightMicros()
                    + ":" + t.cropTopMicros() + ":" + t.cropBottomMicros() + ":" + t.opacityMicros()
                    + ":" + effect.brightness() + ":" + effect.contrast() + ":" + effect.saturation()
                    + ":" + effect.grayscale() + ":" + effect.fadeInSeconds() + ":"
                    + effect.fadeOutSeconds() + ":" + effect.enabled()
                    + ":" + authoredStart + ":" + authoredEnd
                    + dissolve.map(d -> ":" + d.transitionId().length() + ":" + d.transitionId()
                            + ":" + d.outgoingClipId().length() + ":" + d.outgoingClipId()
                            + ":" + d.incomingClipId().length() + ":" + d.incomingClipId()
                            + ":" + d.duration()).orElse("")
                    + opacityKeys.map(k -> ":opacity:" + k.firstTime() + ":" + k.firstValue() + ":" + k.lastTime() + ":" + k.lastValue()).orElse("")
                    + positionKeys.map(p -> ":position:" + p.canonicalSamples()).orElse("")
                    + scaleKeys.map(k -> ":scale:" + k.canonicalSamples()).orElse("");
        }
    }
}
