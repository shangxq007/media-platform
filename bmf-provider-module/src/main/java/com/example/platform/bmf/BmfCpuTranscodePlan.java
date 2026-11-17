package com.example.platform.bmf;

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
 * <p>Single-input units consume one materialized video input and produce one authoritative output;
 * DECODE may carry the authored source window. A COMPOSITE unit with more than one timed base input
 * carries {@link #compositeInputs()} and the exact output duration/frame rate; the inputs must be
 * contiguous and cover the whole propagated extent, because BMF V1 concatenates them and must never
 * compress a timeline gap. The output is always a re-encoded H.264 MP4 produced by the BMF graph.
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
        Optional<com.example.platform.shared.time.FrameRate> compositeFrameRate)
        implements ProviderNativeExecutionPlan {

    /** One base video input positioned on the output timeline (offsets relative to the extent). */
    public record TimedInput(ExecutionInputId inputId, MediaTime start, MediaTime end) {
        public TimedInput {
            Objects.requireNonNull(inputId, "inputId");
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            if (!start.isLessThan(end)) {
                throw new IllegalArgumentException("positive composite input interval required");
            }
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
                Optional.empty(), java.util.List.of(), Optional.empty(), Optional.empty());
    }

    /** Compatibility shape for the single-input plan with an optional decode window. */
    public BmfCpuTranscodePlan(ExecutableTaskId executableTaskId,
            ProviderBindingPin providerBindingPin,
            BmfOperation operation,
            ExecutionInputId inputId,
            ExecutionOutputId outputId,
            Optional<SourceWindow> sourceWindow) {
        this(executableTaskId, providerBindingPin, operation, inputId, outputId, sourceWindow,
                java.util.List.of(), Optional.empty(), Optional.empty());
    }

    public BmfCpuTranscodePlan {
        Objects.requireNonNull(executableTaskId, "executableTaskId");
        Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(inputId, "inputId");
        Objects.requireNonNull(outputId, "outputId");
        Objects.requireNonNull(sourceWindow, "sourceWindow");
        if (!BmfCpuProvider.BINDING.equals(providerBindingPin)) {
            throw new IllegalArgumentException("BMF render unit requires the exact provider binding");
        }
        if (sourceWindow.isPresent() && operation != BmfOperation.DECODE) {
            throw new IllegalArgumentException("only a decode unit may carry a source window");
        }
        Objects.requireNonNull(compositeInputs, "compositeInputs");
        Objects.requireNonNull(compositeDuration, "compositeDuration");
        Objects.requireNonNull(compositeFrameRate, "compositeFrameRate");
        if (compositeInputs.isEmpty()) {
            if (compositeDuration.isPresent() || compositeFrameRate.isPresent()) {
                throw new IllegalArgumentException("composite timing requires composite inputs");
            }
        } else {
            if (operation != BmfOperation.COMPOSITE) {
                throw new IllegalArgumentException("only a composite unit may carry multiple inputs");
            }
            validateComposite(compositeInputs, compositeDuration, compositeFrameRate);
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
