package com.example.platform.ffmpeg;

import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
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
        Optional<SourceWindow> sourceWindow) implements ProviderNativeExecutionPlan {

    /** Bounded V1 operation vocabulary — the typed chain's node operations this provider serves. */
    public enum Operation {
        /** Capability-neutral transcode slice (no capability requirement attached). */
        TRANSCODE,
        /** {@code video.decode}. */
        DECODE,
        /** {@code render.composite}. */
        COMPOSITE,
        /** {@code render.output}. */
        ENCODE
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

    public FfmpegCpuRenderPlan {
        Objects.requireNonNull(executableTaskId, "executableTaskId");
        Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(inputId, "inputId");
        Objects.requireNonNull(outputId, "outputId");
        Objects.requireNonNull(sourceWindow, "sourceWindow");
        if (!FfmpegCpuProvider.BINDING.equals(providerBindingPin)) {
            throw new IllegalArgumentException("FFmpeg CPU plan requires its exact ProviderBindingPin");
        }
        if (sourceWindow.isPresent() && operation != Operation.DECODE) {
            throw new IllegalArgumentException(
                    "only the decode operation carries an authored source sample window");
        }
    }
}
