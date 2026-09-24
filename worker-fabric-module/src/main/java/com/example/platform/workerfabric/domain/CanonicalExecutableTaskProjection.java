package com.example.platform.workerfabric.domain;

import com.example.platform.execution.composition.ProviderLocalCompositionEvaluator;
import com.example.platform.execution.composition.ProviderLocalCompositionRequest;
import com.example.platform.execution.runtime.RuntimeExecutionRequest;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.BoundaryAction;
import java.util.Collection;
import java.util.Objects;

/** Sole projection boundary from a validated runtime request to provider-bound task semantics. */
public final class CanonicalExecutableTaskProjection {
    private CanonicalExecutableTaskProjection() {}
    public static ExecutableTask project(RuntimeExecutionRequest runtime,
            ProviderLocalCompositionRequest composition, Collection<BoundaryAction> actions) {
        Objects.requireNonNull(runtime); Objects.requireNonNull(composition); Objects.requireNonNull(actions);
        var decision = ProviderLocalCompositionEvaluator.evaluate(composition);
        if (!decision.evaluatorProvenAllowed()) throw new IllegalArgumentException("provider composition is not allowed");
        return ExecutableTask.create(decision, actions);
    }
}
