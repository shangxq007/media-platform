package com.example.platform.bmf;

import com.example.platform.render.domain.renderplan.RenderRaster;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionPlan;
import java.util.Objects;
import java.util.Optional;

/**
 * One provider-bound BMF render unit.
 *
 * <p>Decode consumes one materialized video or audio input and may carry an authored source
 * window. Composition consumes extent-relative timed inputs. The legacy video composition
 * requires contiguous clips; multitrack composition carries media type and layer and can fill
 * timeline gaps. Each unit produces one materialized MP4 output.
 */
public record BmfCpuTranscodePlan(
        ExecutableTaskId executableTaskId,
        ProviderBindingPin providerBindingPin,
        BmfOperation operation,
        ExecutionInputId inputId,
        ExecutionOutputId outputId,
        Optional<SourceWindow> sourceWindow,
        java.util.List<TimedInput> compositeInputs,
        Optional<MediaTime> compositeDuration,
        Optional<com.example.platform.shared.time.FrameRate> compositeFrameRate,
        com.example.platform.render.domain.renderplan.RenderRaster raster,
        java.util.List<com.example.platform.render.domain.renderplan.TimedTextMaterializationRequirement.CueRaster> subtitles)
        implements ProviderNativeExecutionPlan {

    public BmfCpuTranscodePlan(ExecutableTaskId executableTaskId, ProviderBindingPin providerBindingPin,
            BmfOperation operation, ExecutionInputId inputId, ExecutionOutputId outputId,
            Optional<SourceWindow> sourceWindow, java.util.List<TimedInput> compositeInputs,
            Optional<MediaTime> compositeDuration, Optional<com.example.platform.shared.time.FrameRate> compositeFrameRate,
            RenderRaster raster) {
        this(executableTaskId, providerBindingPin, operation, inputId, outputId, sourceWindow, compositeInputs,
                compositeDuration, compositeFrameRate, raster, java.util.List.of());
    }

    /**
     * One media input positioned on the output timeline (offsets relative to the extent).
     * Audio inputs carry the canonical route gain/mute/balance values as primitives so the
     * provider module does not depend on the audio domain module.
     */
    public record TimedInput(ExecutionInputId inputId, MediaTime start, MediaTime end, int layer, boolean audio,
                             double gain, boolean mute, double balance,
                             VideoPlacement transform,
                             com.example.platform.render.domain.renderplan.VideoEffectSpec effect,
                             MediaTime elapsed, MediaTime authoredDuration, MediaTime dissolveDuration,
                             Optional<RenderDependency.OpacityRamp> opacityKeys,
                             Optional<RenderDependency.PositionRamp> positionKeys,
                             Optional<RenderDependency.ScaleRamp> scaleKeys,
                             Optional<RenderDependency.GainRamp> gainKeys, RenderDependency.AudioFades fades) {
        public TimedInput(ExecutionInputId inputId, MediaTime start, MediaTime end, int layer, boolean audio,
                          double gain, boolean mute, double balance, VideoPlacement transform,
                          com.example.platform.render.domain.renderplan.VideoEffectSpec effect,
                          MediaTime elapsed, MediaTime authoredDuration, MediaTime dissolveDuration,
                          Optional<RenderDependency.OpacityRamp> opacityKeys, Optional<RenderDependency.PositionRamp> positionKeys,
                          Optional<RenderDependency.ScaleRamp> scaleKeys, Optional<RenderDependency.GainRamp> gainKeys) {
            this(inputId, start, end, layer, audio, gain, mute, balance, transform, effect, elapsed, authoredDuration,
                    dissolveDuration, opacityKeys, positionKeys, scaleKeys, gainKeys, RenderDependency.AudioFades.NONE);
        }

        public TimedInput(ExecutionInputId inputId, MediaTime start, MediaTime end, int layer, boolean audio,
                          double gain, boolean mute, double balance, VideoPlacement transform,
                          com.example.platform.render.domain.renderplan.VideoEffectSpec effect,
                          MediaTime elapsed, MediaTime authoredDuration, MediaTime dissolveDuration,
                          Optional<RenderDependency.OpacityRamp> opacityKeys, Optional<RenderDependency.PositionRamp> positionKeys,
                          Optional<RenderDependency.ScaleRamp> scaleKeys) {
            this(inputId, start, end, layer, audio, gain, mute, balance, transform, effect, elapsed, authoredDuration,
                    dissolveDuration, opacityKeys, positionKeys, scaleKeys, Optional.empty());
        }

        public TimedInput(ExecutionInputId inputId, MediaTime start, MediaTime end, int layer, boolean audio,
                             double gain, boolean mute, double balance,
                             VideoPlacement transform,
                             com.example.platform.render.domain.renderplan.VideoEffectSpec effect,
                             MediaTime elapsed, MediaTime authoredDuration, MediaTime dissolveDuration) {
            this(inputId, start, end, layer, audio, gain, mute, balance, transform, effect, elapsed, authoredDuration, dissolveDuration, Optional.empty(), Optional.empty(), Optional.empty());
        }

        public TimedInput(ExecutionInputId inputId, MediaTime start, MediaTime end) {
            this(inputId, start, end, 0, false, 1.0, false, 0.0,
                    VideoPlacement.IDENTITY,
                    com.example.platform.render.domain.renderplan.VideoEffectSpec.IDENTITY,
                    MediaTime.ZERO, end.subtract(start), MediaTime.ZERO);
        }

        /** Compatibility shape for video-only timed inputs. */
        public TimedInput(ExecutionInputId inputId, MediaTime start, MediaTime end, int layer, boolean audio) {
            this(inputId, start, end, layer, audio, 1.0, false, 0.0,
                    VideoPlacement.IDENTITY,
                    com.example.platform.render.domain.renderplan.VideoEffectSpec.IDENTITY,
                    MediaTime.ZERO, end.subtract(start), MediaTime.ZERO);
        }

        public TimedInput(ExecutionInputId inputId, MediaTime start, MediaTime end, int layer, boolean audio,
                          double gain, boolean mute, double balance) {
            this(inputId, start, end, layer, audio, gain, mute, balance,
                    VideoPlacement.IDENTITY,
                    com.example.platform.render.domain.renderplan.VideoEffectSpec.IDENTITY,
                    MediaTime.ZERO, end.subtract(start), MediaTime.ZERO);
        }

        public TimedInput {
            Objects.requireNonNull(inputId, "inputId");
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            if (Double.isNaN(gain) || Double.isInfinite(gain) || gain < 0.0 || gain > 2.0) {
                throw new IllegalArgumentException("audio gain must be within [0, 2]");
            }
            if (Double.isNaN(balance) || Double.isInfinite(balance) || balance < -1.0 || balance > 1.0) {
                throw new IllegalArgumentException("audio balance must be within [-1, 1]");
            }
            if (!start.isLessThan(end) || start.isLessThan(MediaTime.ZERO) || layer < 0) {
                throw new IllegalArgumentException("positive composite input interval required");
            }
            if (effect == null) effect = com.example.platform.render.domain.renderplan.VideoEffectSpec.IDENTITY;
            Objects.requireNonNull(elapsed, "elapsed");
            Objects.requireNonNull(authoredDuration, "authoredDuration");
            Objects.requireNonNull(fades, "fades");
            if (gainKeys.isPresent()) {
                if (!audio) throw new IllegalArgumentException("Gain envelope requires audio");
                gainKeys.get().validateRelative(authoredDuration.subtract(fades.crossfadeOut()));
            }
            if (fades.active()) {
                if (!audio) throw new IllegalArgumentException("Audio fades require audio");
                fades.requireFits(authoredDuration);
            }
            Objects.requireNonNull(dissolveDuration, "dissolveDuration");
            if (elapsed.ticks() < 0 || dissolveDuration.ticks() < 0
                    || dissolveDuration.isGreaterThan(authoredDuration)
                    || elapsed.add(end.subtract(start)).isGreaterThan(authoredDuration)) {
                throw new IllegalArgumentException("Invalid authored composition phase");
            }
        }
    }

    /** Native scalar projection of the validated canonical transform. */
    public record VideoPlacement(double x, double y, double scaleX, double scaleY, double degrees,
                                 double left, double right, double top, double bottom, double opacity) {
        static final VideoPlacement IDENTITY = new VideoPlacement(0, 0, 1, 1, 0, 0, 1, 0, 1, 1);
        static boolean fitsIntermediateRaster(RenderRaster raster, double scaleX, double scaleY, double degrees) {
            return com.example.platform.execution.compatibility.MediaLoweringEnvelope
                    .fitsIntermediateRaster(raster, scaleX, scaleY, degrees);
        }
    }

    /** Exact authored source interval for a DECODE unit. */
    public record SourceWindow(MediaTime start, MediaTime duration) {
        public SourceWindow {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(duration, "duration");
            if (duration.ticks() <= 0) {
                throw new IllegalArgumentException("source window duration must be positive");
            }
        }
    }

    /** Compatibility shape for the un-windowed transcode unit. */
    public BmfCpuTranscodePlan(ExecutableTaskId executableTaskId,
            ProviderBindingPin providerBindingPin,
            ExecutionInputId inputId,
            ExecutionOutputId outputId) {
        this(executableTaskId, providerBindingPin, BmfOperation.TRANSCODE, inputId, outputId,
                Optional.empty(), java.util.List.of(), Optional.empty(), Optional.empty(),
                com.example.platform.render.domain.renderplan.RenderRaster.DEFAULT);
    }

    /** Compatibility shape for the single-input plan with an optional decode window. */
    public BmfCpuTranscodePlan(ExecutableTaskId executableTaskId,
            ProviderBindingPin providerBindingPin,
            BmfOperation operation,
            ExecutionInputId inputId,
            ExecutionOutputId outputId,
            Optional<SourceWindow> sourceWindow) {
        this(executableTaskId, providerBindingPin, operation, inputId, outputId, sourceWindow,
                java.util.List.of(), Optional.empty(), Optional.empty(),
                com.example.platform.render.domain.renderplan.RenderRaster.DEFAULT);
    }

    public BmfCpuTranscodePlan {
        Objects.requireNonNull(executableTaskId, "executableTaskId");
        Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(inputId, "inputId");
        Objects.requireNonNull(outputId, "outputId");
        Objects.requireNonNull(sourceWindow, "sourceWindow");
        Objects.requireNonNull(raster, "raster");
        if (!BmfCpuProvider.recognizes(providerBindingPin)) {
            throw new IllegalArgumentException("BMF render unit requires the exact provider binding");
        }
        if (sourceWindow.isPresent() && operation != BmfOperation.DECODE && operation != BmfOperation.AUDIO_DECODE) {
            throw new IllegalArgumentException("only a decode unit may carry a source window");
        }
        compositeInputs = java.util.List.copyOf(compositeInputs);
        subtitles = java.util.List.copyOf(subtitles);
        if (!subtitles.isEmpty() && (operation != BmfOperation.MULTITRACK_COMPOSITE || subtitles.size() > 16)) {
            throw new IllegalArgumentException("Subtitle blocks require a bounded multitrack composition");
        }
        Objects.requireNonNull(compositeDuration, "compositeDuration");
        Objects.requireNonNull(compositeFrameRate, "compositeFrameRate");
        if (compositeInputs.isEmpty()) {
            if (compositeDuration.isPresent() != compositeFrameRate.isPresent()
                    || compositeDuration.isPresent() && (operation != BmfOperation.ENCODE && operation != BmfOperation.MULTITRACK_ENCODE
                    || !compositeDuration.orElseThrow().isGreaterThan(MediaTime.ZERO))) {
                throw new IllegalArgumentException("output timing requires a final encoder and a positive extent");
            }
        } else {
            if (!inputId.equals(compositeInputs.getFirst().inputId())
                    || compositeInputs.stream().map(TimedInput::inputId).distinct().count() != compositeInputs.size()) {
                throw new IllegalArgumentException("composition input identities must be unique");
            }
            if (operation == BmfOperation.MULTITRACK_COMPOSITE) {
                if (compositeDuration.isEmpty() || compositeFrameRate.isEmpty()
                        || compositeInputs.stream().anyMatch(input -> input.end().isGreaterThan(compositeDuration.orElseThrow()))) {
                    throw new IllegalArgumentException("multitrack inputs require an exact composition extent");
                }
            } else if (operation == BmfOperation.COMPOSITE) {
                validateComposite(compositeInputs, compositeDuration, compositeFrameRate);
            } else {
                throw new IllegalArgumentException("only a composite unit may carry multiple inputs");
            }
        }
    }

    private static void validateComposite(java.util.List<TimedInput> compositeInputs,
            Optional<MediaTime> compositeDuration,
            Optional<com.example.platform.shared.time.FrameRate> compositeFrameRate) {
        if (compositeDuration.isEmpty() || compositeFrameRate.isEmpty()) {
            throw new IllegalArgumentException("composite units require duration and frame rate");
        }
        MediaTime duration = compositeDuration.orElseThrow();
        if (duration.ticks() <= 0) {
            throw new IllegalArgumentException("composite duration must be positive");
        }
        if (!compositeInputs.getFirst().start().equals(MediaTime.ZERO)) {
            throw new IllegalArgumentException("composite inputs must start at the output origin");
        }
        for (int index = 0; index < compositeInputs.size(); index++) {
            TimedInput input = compositeInputs.get(index);
            if (!input.end().isLessThanOrEqualTo(duration)) {
                throw new IllegalArgumentException("composite input exceeds the output duration");
            }
            if (index > 0 && !compositeInputs.get(index - 1).end().equals(input.start())) {
                throw new IllegalArgumentException("composite inputs must be contiguous");
            }
        }
        if (!compositeInputs.getLast().end().equals(duration)) {
            throw new IllegalArgumentException("composite inputs must cover the whole output");
        }
    }
}
