package com.example.platform.workerfabric.domain;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskGraphDigest;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import java.util.List;

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
        ProviderCandidate provider = provider(providerName);
        ExecutableTask task = mock(ExecutableTask.class);
        ProviderBoundExecutableTaskGraph graph = mock(ProviderBoundExecutableTaskGraph.class);
        when(task.id()).thenReturn(new ExecutableTaskId("1".repeat(64)));
        ProviderBindingPin binding = provider.bindingPin();
        when(task.providerBindingPin()).thenReturn(binding);
        when(task.memberships()).thenReturn(List.of());
        when(graph.tasks()).thenReturn(List.of(task));
        when(graph.digest()).thenReturn(new ExecutableTaskGraphDigest("2".repeat(64)));
        return new Scenario(graph, task, provider);
    }

    static ProviderCandidate provider(String name) {
        ProviderBindingPin binding = mock(ProviderBindingPin.class, name + "-binding");
        ProviderCandidate provider = mock(ProviderCandidate.class, name + "-candidate");
        when(provider.bindingPin()).thenReturn(binding);
        return provider;
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
