package com.example.platform.execution.taskgraph;

import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.BoundaryContractId;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import java.util.Objects;
import java.util.Optional;

/**
 * Canonical #22 execution-lowering boundary for one cross-provider dependency.
 *
 * <p>It is intentionally distinct from the upstream #21 mandatory materialization type and carries
 * only an expected materialization contract, never a pre-invented output Artifact identity.
 */
public record CrossProviderArtifactBoundary(
        LogicalDependencyEdge sourceDependency,
        ExecutionStepId producerUnitId,
        ExecutionStepId consumerUnitId,
        ProviderBindingPin producerBindingPin,
        ProviderBindingPin consumerBindingPin,
        OutputDeclaration producerOutput,
        InputBinding consumerInput,
        MaterializationContract materializationContract,
        Optional<BoundaryContractId> interoperabilityContract) {

    public static final boolean INDEPENDENTLY_SCHEDULABLE = false;

    public CrossProviderArtifactBoundary {
        Objects.requireNonNull(sourceDependency, "sourceDependency");
        Objects.requireNonNull(producerUnitId, "producerUnitId");
        Objects.requireNonNull(consumerUnitId, "consumerUnitId");
        Objects.requireNonNull(producerBindingPin, "producerBindingPin");
        Objects.requireNonNull(consumerBindingPin, "consumerBindingPin");
        Objects.requireNonNull(producerOutput, "producerOutput");
        Objects.requireNonNull(consumerInput, "consumerInput");
        Objects.requireNonNull(materializationContract, "materializationContract");
        Objects.requireNonNull(interoperabilityContract, "interoperabilityContract");
        if (producerUnitId.equals(consumerUnitId)
                || producerBindingPin.equals(consumerBindingPin)) {
            throw new IllegalArgumentException(
                    "cross-provider boundary requires distinct units and exact bindings");
        }
        if (!sourceDependency.producerLogicalNodeId().equals(producerOutput.logicalNodeId())
                || !sourceDependency.consumerLogicalNodeId()
                        .equals(consumerInput.consumerLogicalNodeId())
                || !sourceDependency.producerLogicalNodeId()
                        .equals(consumerInput.producerLogicalNodeId())
                || !producerUnitId.equals(consumerInput.producerStepId())
                || !consumerUnitId.equals(consumerInput.consumerStepId())
                || !sourceDependency.dependencyVariant()
                        .equals(consumerInput.dependencyVariant())
                || consumerInput.sourceArtifact() != null) {
            throw new IllegalArgumentException(
                    "cross-provider boundary must retain exact typed source output/input mapping "
                            + "without pre-inventing an output Artifact pin");
        }
    }

    public enum MaterializationContract {
        IMMUTABLE_ARTIFACT_AUTHORITY_V1
    }
}
