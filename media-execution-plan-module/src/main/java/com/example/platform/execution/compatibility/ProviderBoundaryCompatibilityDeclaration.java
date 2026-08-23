package com.example.platform.execution.compatibility;

import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.BoundaryContractId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import java.util.Objects;

/** Exact typed interoperability evidence for one dependency and provider-binding pair. */
public record ProviderBoundaryCompatibilityDeclaration(
        LogicalDependencyEdge sourceDependency,
        ProviderBindingPin producerBindingPin,
        ProviderBindingPin consumerBindingPin,
        BoundaryContractId boundaryContractId,
        Declaration declaration) {

    public ProviderBoundaryCompatibilityDeclaration {
        Objects.requireNonNull(sourceDependency, "sourceDependency");
        Objects.requireNonNull(producerBindingPin, "producerBindingPin");
        Objects.requireNonNull(consumerBindingPin, "consumerBindingPin");
        Objects.requireNonNull(boundaryContractId, "boundaryContractId");
        Objects.requireNonNull(declaration, "declaration");
    }

    public enum Declaration {
        DIRECT_INTEROPERABILITY_ALLOWED,
        ARTIFACT_MATERIALIZATION_REQUIRED,
        INCOMPATIBLE,
        UNKNOWN_FAIL_CLOSED
    }
}
