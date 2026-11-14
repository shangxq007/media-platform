package com.example.platform.ffmpeg;

import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionPlan;
import java.util.Objects;
import java.util.Optional;

/**
 * Typed, derived CPU ffmpeg runtime plan for one exact provider-bound task.
 *
 * <p>Bounded V1 slice: the provider lowers one video stream per task. In this slice
 * every operation normalizes that single stream to the same canonical H.264 /
 * yuv420p mp4 stdout intermediate; {@link Operation#DECODE} additionally honours the
 * exact authored source sample window. Operations differ by their declared stage
 * semantics, not by container or codec choice.</p>
 *
 * <p>Not canonical media state, not a timeline/product authority, not a scheduling
 * or placement decision, and not a provider-selection authority.</p>
 */
public record FfmpegCpuRenderPlan(
        ExecutableTaskId executableTaskId,
        ProviderBindingPin providerBindingPin,
        Operation operation,
        ExecutionInputId inputId,
        ExecutionOutputId outputId,
        Optional<SourceWindow> sourceWindow,
        Optional<com.example.platform.shared.capability.MediaFrameExtractParametersV1> frameParameters,
        java.util.List<TimedInput> compositeInputs,
        Optional<MediaTime> compositeDuration,
        Optional<FrameRate> outputFrameRate,
        Optional<RasterParameters> rasterParameters) implements ProviderNativeExecutionPlan {

    /** Bounded V1 operation vocabulary — the typed chain's node operations this provider serves. */
    public enum Operation {
        /** Capability-neutral transcode slice (no capability requirement attached). */
        FRAME_EXTRACT,
        TRANSCODE,
        /** {@code video.decode}. */
        DECODE,
        /** {@code render.composite}. */
        COMPOSITE,
        /** {@code render.output}. */
        ENCODE,
        /** {@code subtitle.rasterize}. */
        RASTER
    }

    /**
     * Bounded V1 timed-text rasterization parameters derived from the typed
     * {@code TimedTextMaterializationRequirement}. Exact text content and timing; the font is a
     * declared worker execution dependency, never a request-supplied path.
     *
     * @param text exact authored text content (escaped by the adapter)
     * @param start exact render-extent-relative start
     * @param end exact render-extent-relative end
     * @param fontSize authored font size in pixels
     * @param fontFile declared worker font file (absolute, normalized)
     * @param x horizontal draw position (FFmpeg drawtext expression)
     * @param y vertical draw position (FFmpeg drawtext expression)
     */
    public record RasterParameters(
            String text,
            MediaTime start,
            MediaTime end,
            MediaTime duration,
            int fontSize,
            String fontFile,
            String x,
            String y) {
        public RasterParameters {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            Objects.requireNonNull(duration, "duration");
            Objects.requireNonNull(fontFile, "fontFile");
            Objects.requireNonNull(x, "x");
            Objects.requireNonNull(y, "y");
            if (start.isGreaterThan(end)) {
                throw new IllegalArgumentException("raster interval start must be <= end");
            }
            if (!duration.isGreaterThan(MediaTime.ZERO) || end.isGreaterThan(duration)) {
                throw new IllegalArgumentException("raster duration must bound the text interval");
            }
            if (fontSize <= 0) {
                throw new IllegalArgumentException("font size must be > 0");
            }
        }
    }

    /** Exact authored source sample window in source coordinates. */
    public record SourceWindow(MediaTime start, MediaTime end) {

        public SourceWindow {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            if (start.isGreaterThan(end)) {
                throw new IllegalArgumentException("source window start must be <= end");
            }
        }

        /** Exact window duration (end - start). */
        public MediaTime duration() {
            return end.subtract(start);
        }
    }

    /** Exact Timeline placement, relative to the requested render extent. */
    public record TimedInput(ExecutionInputId inputId, MediaTime start, MediaTime end, boolean overlay) {
        public TimedInput(ExecutionInputId inputId, MediaTime start, MediaTime end) {
            this(inputId, start, end, false);
        }
        public TimedInput {
            Objects.requireNonNull(inputId); Objects.requireNonNull(start); Objects.requireNonNull(end);
            if (!start.isLessThan(end)) throw new IllegalArgumentException("positive input interval required");
        }
    }

    public FfmpegCpuRenderPlan(ExecutableTaskId executableTaskId, ProviderBindingPin providerBindingPin,
            Operation operation, ExecutionInputId inputId, ExecutionOutputId outputId, Optional<SourceWindow> sourceWindow) {
        this(executableTaskId,providerBindingPin,operation,inputId,outputId,sourceWindow,Optional.empty(),
                java.util.List.of(),Optional.empty(),Optional.empty(),Optional.empty());
    }

    public FfmpegCpuRenderPlan(ExecutableTaskId executableTaskId, ProviderBindingPin providerBindingPin,
            Operation operation, ExecutionInputId inputId, ExecutionOutputId outputId,
            Optional<SourceWindow> sourceWindow,
            Optional<com.example.platform.shared.capability.MediaFrameExtractParametersV1> frameParameters) {
        this(executableTaskId,providerBindingPin,operation,inputId,outputId,sourceWindow,frameParameters,
                java.util.List.of(),Optional.empty(),Optional.empty(),Optional.empty());
    }

    public FfmpegCpuRenderPlan {
        Objects.requireNonNull(executableTaskId, "executableTaskId");
        Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        Objects.requireNonNull(operation, "operation");
        if (operation != Operation.RASTER) {
            Objects.requireNonNull(inputId, "inputId");
        }
        Objects.requireNonNull(outputId, "outputId");
        Objects.requireNonNull(sourceWindow, "sourceWindow");
        Objects.requireNonNull(frameParameters, "frameParameters");
        Objects.requireNonNull(rasterParameters, "rasterParameters");
        compositeInputs = java.util.List.copyOf(compositeInputs);
        Objects.requireNonNull(compositeDuration, "compositeDuration");
        if (!compositeInputs.isEmpty() && (operation != Operation.COMPOSITE
                || compositeInputs.size() < 1 || compositeDuration.isEmpty())) {
            throw new IllegalArgumentException("timed multi-input composition requires its duration");
        }
        if (!compositeInputs.isEmpty() && (compositeInputs.stream().map(TimedInput::inputId).distinct().count()
                != compositeInputs.size() || !inputId.equals(compositeInputs.getFirst().inputId())
                || compositeInputs.stream().anyMatch(input -> input.end().isGreaterThan(compositeDuration.orElseThrow())))) {
            throw new IllegalArgumentException("composition inputs must be unique and inside its extent");
        }
        if (compositeInputs.isEmpty() && compositeDuration.isPresent()) {
            throw new IllegalArgumentException("composition duration requires timed inputs");
        }
        if ((operation == Operation.FRAME_EXTRACT) != frameParameters.isPresent()) {
            throw new IllegalArgumentException("frame extraction requires exactly its typed parameters");
        }
        if ((operation == Operation.RASTER) != rasterParameters.isPresent()) {
            throw new IllegalArgumentException("rasterization requires exactly its typed parameters");
        }
        if (!FfmpegCpuProvider.BINDING.equals(providerBindingPin)) {
            throw new IllegalArgumentException("FFmpeg CPU plan requires its exact ProviderBindingPin");
        }
        if (sourceWindow.isPresent() && operation != Operation.DECODE) {
            throw new IllegalArgumentException(
                    "only the decode operation carries an authored source sample window");
        }
    }
}
