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
 * <p>V1 is bounded to a single materialized video input and one authoritative output. DECODE may
 * carry the authored source window; other operations run on the whole materialized input. The
 * output is always a re-encoded H.264 MP4 produced by the BMF graph (resolution and frame rate
 * follow the materialized input; bounded V1 does not rescale).
 */
public record BmfCpuTranscodePlan(
        ExecutableTaskId executableTaskId,
        ProviderBindingPin providerBindingPin,
        BmfOperation operation,
        ExecutionInputId inputId,
        ExecutionOutputId outputId,
        Optional<SourceWindow> sourceWindow) implements ProviderNativeExecutionPlan {

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
                Optional.empty());
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
    }
}
