package com.example.platform.workerfabric.domain;

import com.example.platform.execution.taskgraph.ExecutableTask;

/** Pure bounded eligibility contract; it performs no farm submission or placement. */
@FunctionalInterface
public interface OpenCueBackendEligibilityContract {

    ExecutionBackendEligibilityDecision evaluate(
            ExecutableTask executableTask,
            ProviderBackendExecutionSupport providerBackendExecutionSupport);

    static OpenCueBackendEligibilityContract canonical() {
        return (task, support) -> ExecutionBackendEligibilityEvaluator.evaluate(
                task, support, ExecutionBackend.OPEN_CUE_FARM);
    }
}
