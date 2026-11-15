package com.example.platform.bmf;

import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionPlan;
import java.util.Objects;

/** One provider-bound, single-input video transcode. The output is a 64x48 H.264 MP4. */
public record BmfCpuTranscodePlan(
        ExecutableTaskId executableTaskId,
        ProviderBindingPin providerBindingPin,
        ExecutionInputId inputId,
        ExecutionOutputId outputId) implements ProviderNativeExecutionPlan {
    public BmfCpuTranscodePlan {
        Objects.requireNonNull(executableTaskId, "executableTaskId");
        Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        Objects.requireNonNull(inputId, "inputId");
        Objects.requireNonNull(outputId, "outputId");
        if (!BmfCpuProvider.BINDING.equals(providerBindingPin)) {
            throw new IllegalArgumentException("BMF transcode requires the exact provider binding");
        }
    }
}
