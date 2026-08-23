package com.example.platform.execution.compatibility;

import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.BoundaryContractId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import java.util.Objects;
import java.util.Optional;

/** Immutable pairwise Stage-1 transition result for one exact source dependency. */
public record ProviderCompatibilityTransition(
        LogicalDependencyEdge sourceDependency,
        PhysicalPlanUnit producerUnit,
        ProviderBindingPin producerBindingPin,
        PhysicalPlanUnit consumerUnit,
        ProviderBindingPin consumerBindingPin,
        ProviderCompatibilityTransitionDecision decision,
        Optional<BoundaryContractId> boundaryContractId) {

    public ProviderCompatibilityTransition {
        Objects.requireNonNull(sourceDependency, "sourceDependency");
        Objects.requireNonNull(producerUnit, "producerUnit");
        Objects.requireNonNull(producerBindingPin, "producerBindingPin");
        Objects.requireNonNull(consumerUnit, "consumerUnit");
        Objects.requireNonNull(consumerBindingPin, "consumerBindingPin");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(boundaryContractId, "boundaryContractId");
        if (!sourceDependency.producerLogicalNodeId().equals(producerUnit.logicalNodeId())
                || !sourceDependency.consumerLogicalNodeId().equals(consumerUnit.logicalNodeId())) {
            throw new IllegalArgumentException(
                    "transition dependency must bind its exact producer and consumer units");
        }
        if (decision == ProviderCompatibilityTransitionDecision.DIRECT_COMPATIBLE
                && !producerBindingPin.equals(consumerBindingPin)
                && boundaryContractId.isEmpty()) {
            throw new IllegalArgumentException(
                    "direct cross-provider transition requires typed interoperability evidence");
        }
    }
}
