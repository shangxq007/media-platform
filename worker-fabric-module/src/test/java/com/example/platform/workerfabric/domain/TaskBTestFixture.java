package com.example.platform.workerfabric.domain;

import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;

/**
 * Worker-visible Task B boundary fixture.
 *
 * <p>The public physical-plan constructors expose render-domain types that are intentionally absent
 * from worker-fabric's compile classpath. Mocking only the immutable media-execution boundary keeps
 * these tests at the worker-fabric domain level while real selections and evaluators prove B1-B12.
 */
final class TaskBTestFixture {

    private TaskBTestFixture() {}

    static Scenario scenario(String providerName, String unitName) {
        return scenario(providerName, unitName, new ExecutableTaskId("1".repeat(64)));
    }

    static Scenario scenario(
            String providerName, String unitName, ExecutableTaskId executableTaskId) {
        I4TestFixture.StageOneScenario exact =
                I4TestFixture.stageOneScenario(providerName, unitName, executableTaskId);
        return new Scenario(exact.graph(), exact.task(), exact.provider());
    }

    static ProviderCandidate provider(String name) {
        return I4TestFixture.provider(name);
    }

    static ExecutionBackendSelection selection(Scenario scenario, ExecutionBackend backend) {
        ProviderBackendExecutionSupport support = ProviderBackendExecutionSupport.declared(
                scenario.task().providerBindingPin(), java.util.Set.of(backend));
        return ExecutionBackendSelection.select(
                scenario.graph(),
                scenario.task(),
                ExecutionBackendEligibilityEvaluator.evaluate(scenario.task(), support, backend));
    }

    record Scenario(
            ProviderBoundExecutableTaskGraph graph,
            ExecutableTask task,
            ProviderCandidate provider) {}
}
