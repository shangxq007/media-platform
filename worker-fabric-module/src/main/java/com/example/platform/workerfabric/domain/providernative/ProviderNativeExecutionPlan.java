package com.example.platform.workerfabric.domain.providernative;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;

/**
 * Derived provider-native runtime plan for exactly one already-provider-bound executable task.
 *
 * <p>This is not canonical domain state, Timeline state, RenderPlan state, PhysicalExecutionPlan
 * authority, scheduler state, worker assignment state, or a provider selection authority.
 */
public interface ProviderNativeExecutionPlan {

    ExecutableTaskId executableTaskId();

    ProviderBindingPin providerBindingPin();

    default void requireTaskAndBinding(
            ExecutableTaskId expectedTaskId,
            ProviderBindingPin expectedBindingPin,
            ProviderNativeFailureCode mismatchCode) {
        if (!executableTaskId().equals(expectedTaskId)
                || !providerBindingPin().equals(expectedBindingPin)) {
            throw new ProviderNativeExecutionFailure(
                    mismatchCode,
                    "provider-native plan task/binding mismatch: fail closed");
        }
    }
}
