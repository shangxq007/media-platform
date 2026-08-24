package com.example.platform.workerfabric.domain;

import com.example.platform.execution.taskgraph.ExecutableTask;

/** Pure bounded eligibility contract; it does not invoke a remote provider. */
@FunctionalInterface
public interface RemoteProviderBackendEligibilityContract {

    ExecutionBackendEligibilityDecision evaluate(
            ExecutableTask executableTask,
            ProviderBackendExecutionSupport providerBackendExecutionSupport);

    static RemoteProviderBackendEligibilityContract canonical() {
        return (task, support) -> ExecutionBackendEligibilityEvaluator.evaluate(
                task, support, ExecutionBackend.REMOTE_PROVIDER);
    }
}
