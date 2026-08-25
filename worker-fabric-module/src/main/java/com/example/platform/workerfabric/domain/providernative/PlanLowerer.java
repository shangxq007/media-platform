package com.example.platform.workerfabric.domain.providernative;

import com.example.platform.execution.taskgraph.ExecutableTask;

/**
 * Pure deterministic provider-native lowering SPI for one ExecutableTask.
 *
 * <p>Implementations must fail closed, use only immutable/static input, preserve the upstream
 * ProviderBindingPin exactly, and must not read mutable runtime placement/device/lease/probe state.
 */
@FunctionalInterface
public interface PlanLowerer<P extends ProviderNativeExecutionPlan> {

    P lower(ExecutableTask task, StaticProviderExecutionContext context)
            throws ProviderNativeExecutionFailure;
}
