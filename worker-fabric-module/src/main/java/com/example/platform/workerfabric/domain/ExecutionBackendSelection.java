package com.example.platform.workerfabric.domain;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import java.util.Objects;

/** Runtime backend binding created only from an evaluator-proven eligible decision. */
public final class ExecutionBackendSelection {

    private final ExecutableTask task;
    private final ExecutionBackend backend;

    private ExecutionBackendSelection(ExecutableTask task, ExecutionBackend backend) {
        this.task = task;
        this.backend = backend;
    }

    public static ExecutionBackendSelection select(
            ProviderBoundExecutableTaskGraph providerBoundGraph,
            ExecutableTask task,
            ExecutionBackendEligibilityDecision decision) {
        Objects.requireNonNull(providerBoundGraph, "providerBoundGraph");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(decision, "decision");
        if (providerBoundGraph.tasks().stream().noneMatch(candidate -> candidate == task)) {
            throw new IllegalArgumentException(
                    "backend selection occurs only after and against the exact provider-bound ETG");
        }
        var proof = decision.requireProof();
        if (!proof.proves(task.id(), task.providerBindingPin(), decision.backend())) {
            throw new IllegalArgumentException(
                    "backend eligibility decision must bind the exact provider-bound task");
        }
        return new ExecutionBackendSelection(task, decision.backend());
    }

    public ExecutableTaskId executableTaskId() {
        return task.id();
    }

    /** Derived from the immutable task; selection has no provider rebinding field. */
    public ProviderBindingPin providerBindingPin() {
        return task.providerBindingPin();
    }

    public ExecutionBackend backend() {
        return backend;
    }

    public PlacementAuthorityScope placementAuthorityScope() {
        return backend.placementAuthorityScope();
    }

    boolean selectsExactTask(ExecutableTask candidate) {
        return task == candidate;
    }
}
