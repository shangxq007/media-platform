package com.example.platform.bmf;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionPlan;
import java.util.Objects;

/** Opaque BMF-native plan containing no media or runtime mechanics. */
public record BmfCpuNativePlan(
        ExecutableTaskId executableTaskId,
        ProviderBindingPin providerBindingPin) implements ProviderNativeExecutionPlan {

    public BmfCpuNativePlan {
        Objects.requireNonNull(executableTaskId, "executableTaskId");
        Objects.requireNonNull(providerBindingPin, "providerBindingPin");
    }
}
