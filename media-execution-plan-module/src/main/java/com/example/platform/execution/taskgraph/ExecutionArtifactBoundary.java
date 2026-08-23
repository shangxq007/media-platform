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
 * Canonical #22 execution-transfer boundary for one inter-task dependency.
 *
 * <p>It is intentionally distinct from the upstream #21 mandatory materialization type and carries
 * only an expected materialization contract, never a pre-invented output Artifact identity.
 */
public record ExecutionArtifactBoundary(
        LogicalDependencyEdge sourceDependency,
        ExecutionStepId producerUnitId,
        ExecutionStepId consumerUnitId,
        ProviderBindingPin producerBindingPin,
        ProviderBindingPin consumerBindingPin,
        OutputDeclaration producerOutput,
        InputBinding consumerInput,
        MaterializationContract materializationContract,
        MaterializationReason reason,
        Optional<BoundaryContractId> interoperabilityContract) {

    public static final boolean INDEPENDENTLY_SCHEDULABLE = false;

    public ExecutionArtifactBoundary {
        Objects.requireNonNull(sourceDependency, "sourceDependency");
        Objects.requireNonNull(producerUnitId, "producerUnitId");
        Objects.requireNonNull(consumerUnitId, "consumerUnitId");
        Objects.requireNonNull(producerBindingPin, "producerBindingPin");
        Objects.requireNonNull(consumerBindingPin, "consumerBindingPin");
        Objects.requireNonNull(producerOutput, "producerOutput");
        Objects.requireNonNull(consumerInput, "consumerInput");
        Objects.requireNonNull(materializationContract, "materializationContract");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(interoperabilityContract, "interoperabilityContract");
        if (producerUnitId.equals(consumerUnitId)) {
            throw new IllegalArgumentException(
                    "execution Artifact boundary requires distinct producer and consumer units");
        }
        if (reason == MaterializationReason.PROVIDER_BINDING_CHANGE
                && producerBindingPin.equals(consumerBindingPin)) {
            throw new IllegalArgumentException(
                    "provider-binding-change materialization requires distinct bindings");
        }
        if (reason == MaterializationReason.INTER_TASK_RUNTIME_BOUNDARY_UNPROVEN
                && !producerBindingPin.equals(consumerBindingPin)) {
            throw new IllegalArgumentException(
                    "unproven-runtime-boundary materialization requires the same binding");
        }
        if (!sourceDependency.producerLogicalNodeId().equals(producerOutput.logicalNodeId())
                || !sourceDependency.producerRenderNodeId()
                        .equals(producerOutput.sourceRenderNodeId())
                || !sourceDependency.consumerLogicalNodeId()
                        .equals(consumerInput.consumerLogicalNodeId())
                || !sourceDependency.consumerRenderNodeId()
                        .equals(consumerInput.consumerRenderNodeId())
                || !sourceDependency.producerLogicalNodeId()
                        .equals(consumerInput.producerLogicalNodeId())
                || !sourceDependency.producerRenderNodeId()
                        .equals(consumerInput.producerRenderNodeId())
                || !producerUnitId.equals(consumerInput.producerStepId())
                || !consumerUnitId.equals(consumerInput.consumerStepId())
                || !sourceDependency.dependencyVariant()
                        .equals(consumerInput.dependencyVariant())
                || consumerInput.sourceArtifact() != null) {
            throw new IllegalArgumentException(
                    "execution Artifact boundary must retain exact typed source output/input mapping "
                            + "without pre-inventing an output Artifact pin");
        }
    }

    public enum MaterializationContract {
        IMMUTABLE_ARTIFACT_AUTHORITY_V1
    }

    public enum MaterializationReason {
        PROVIDER_BINDING_CHANGE,
        INTER_TASK_RUNTIME_BOUNDARY_UNPROVEN,
        EXPLICIT_MATERIALIZATION_REQUIREMENT
    }
}
