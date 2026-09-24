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
        if (runtime.ownershipGeneration() < 1) {
            throw new IllegalArgumentException("runtime request requires a positive admitted ownership generation");
        }
        boolean capabilityDeclared = composition.providerExecutionContract().capabilityContractReferences().stream()
                .anyMatch(reference -> reference.capabilityId().value().equals(runtime.capabilityId()));
        if (!capabilityDeclared) {
            throw new IllegalArgumentException("runtime capability is absent from provider execution contract");
        }
        var decision = ProviderLocalCompositionEvaluator.evaluate(composition);
        if (!decision.evaluatorProvenAllowed()) throw new IllegalArgumentException("provider composition is not allowed");
        return ExecutableTask.create(decision, actions);
    }
}
