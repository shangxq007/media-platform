package com.example.platform.workerfabric.reuse;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.execution.taskgraph.Cacheability;

import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Tenant-scoped request for validated reuse pruning and execution of a provider-bound graph. */
public record RuntimeClosedLoopRequest(
        String tenantId,
        ProviderBoundExecutableTaskGraph graph,
        Set<ExecutableTaskId> requestedTasks,
        Map<ExecutableTaskId, Cacheability> cacheability,
        Map<ExecutableTaskId, TaskRuntimeExecution> taskExecutions,
        Map<ExecutableTaskId, ArtifactPin> seedOutputPins) {

    public RuntimeClosedLoopRequest(
            String tenantId,
            ProviderBoundExecutableTaskGraph graph,
            Set<ExecutableTaskId> requestedTasks,
            Map<ExecutableTaskId, Cacheability> cacheability,
            Map<ExecutableTaskId, TaskRuntimeExecution> taskExecutions) {
        this(tenantId, graph, requestedTasks, cacheability, taskExecutions, Map.of());
    }

    public RuntimeClosedLoopRequest {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(requestedTasks, "requestedTasks");
        Objects.requireNonNull(cacheability, "cacheability");
        Objects.requireNonNull(taskExecutions, "taskExecutions");
        Objects.requireNonNull(seedOutputPins, "seedOutputPins");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        requestedTasks = Set.copyOf(requestedTasks);
        cacheability = Map.copyOf(cacheability);
        taskExecutions = Map.copyOf(taskExecutions);
        seedOutputPins = Map.copyOf(seedOutputPins);
    }
}
