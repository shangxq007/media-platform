package com.example.platform.execution.taskgraph;

import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import com.example.platform.render.domain.renderplan.RenderArtifactReference.FinalArtifactExpectation;
import com.example.platform.render.domain.renderplan.RenderArtifactReference.IntermediateArtifactExpectation;
import com.example.platform.render.domain.renderplan.RenderMaterializationRequirement;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable pre/post semantic owned by its containing primary task.
 * It has no scheduler identity and is never independently schedulable.
 */
public record BoundaryAction(Phase phase, int deterministicOrder, Target target) {

    public static final boolean INDEPENDENTLY_SCHEDULABLE = false;
    public static final boolean OUTPUT_SUCCESS_REQUIRES_ARTIFACT_AUTHORITY_COMMIT = true;

    public BoundaryAction {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(target, "target");
        if (deterministicOrder < 0) {
            throw new IllegalArgumentException("deterministicOrder must be non-negative");
        }
    }

    public enum Phase {
        PRE_EXECUTION,
        POST_EXECUTION
    }

    /** Typed artifact/materialization target; no storage location or transfer behavior. */
    public sealed interface Target permits RequiredInputArtifactTarget,
            IntermediateArtifactTarget, FinalArtifactTarget, MandatoryMaterializationTarget,
            CrossProviderMaterializeTarget, CrossProviderAcquireTarget {

        ExecutionStepId memberUnitId();
    }

    /** Pinned immutable input acquired/staged before primary task execution. */
    public record RequiredInputArtifactTarget(
            ExecutionStepId memberUnitId,
            InputBinding inputBinding) implements Target {

        public RequiredInputArtifactTarget {
            Objects.requireNonNull(memberUnitId, "memberUnitId");
            Objects.requireNonNull(inputBinding, "inputBinding");
            if (inputBinding.sourceArtifact() == null) {
                throw new IllegalArgumentException("required input target needs a pinned SourceArtifact");
            }
            if (!memberUnitId.equals(inputBinding.consumerStepId())) {
                throw new IllegalArgumentException("input target must belong to the member consumer");
            }
        }
    }

    /** Declared intermediate output committed after primary task execution. */
    public record IntermediateArtifactTarget(
            ExecutionStepId memberUnitId,
            OutputDeclaration outputDeclaration,
            IntermediateArtifactExpectation artifactTarget) implements Target {

        public IntermediateArtifactTarget {
            Objects.requireNonNull(memberUnitId, "memberUnitId");
            Objects.requireNonNull(outputDeclaration, "outputDeclaration");
            Objects.requireNonNull(artifactTarget, "artifactTarget");
            if (!outputDeclaration.intermediateArtifactExpectations().contains(artifactTarget)) {
                throw new IllegalArgumentException("intermediate artifact must be declared by the output");
            }
        }
    }

    /** Declared final output committed after primary task execution. */
    public record FinalArtifactTarget(
            ExecutionStepId memberUnitId,
            OutputDeclaration outputDeclaration,
            FinalArtifactExpectation artifactTarget) implements Target {

        public FinalArtifactTarget {
            Objects.requireNonNull(memberUnitId, "memberUnitId");
            Objects.requireNonNull(outputDeclaration, "outputDeclaration");
            Objects.requireNonNull(artifactTarget, "artifactTarget");
            if (!outputDeclaration.finalArtifactExpectations().contains(artifactTarget)) {
                throw new IllegalArgumentException("final artifact must be declared by the output");
            }
        }
    }

    /** Mandatory immutable materialization at an output/dependency boundary. */
    public record MandatoryMaterializationTarget(
            ExecutionStepId memberUnitId,
            OutputDeclaration outputDeclaration,
            RenderMaterializationRequirement materializationRequirement,
            Optional<LogicalDependencyEdge> dependencyTarget) implements Target {

        public MandatoryMaterializationTarget {
            Objects.requireNonNull(memberUnitId, "memberUnitId");
            Objects.requireNonNull(outputDeclaration, "outputDeclaration");
            Objects.requireNonNull(materializationRequirement, "materializationRequirement");
            Objects.requireNonNull(dependencyTarget, "dependencyTarget");
            if (!outputDeclaration.materializationRequirements().contains(materializationRequirement)) {
                throw new IllegalArgumentException("materialization target must be declared by the output");
            }
        }
    }

    /** #22 producer-side POST_EXECUTION materialization; references one canonical boundary. */
    public record CrossProviderMaterializeTarget(
            CrossProviderArtifactBoundary boundary) implements Target {

        public CrossProviderMaterializeTarget {
            Objects.requireNonNull(boundary, "boundary");
        }

        @Override
        public ExecutionStepId memberUnitId() {
            return boundary.producerUnitId();
        }
    }

    /** #22 consumer-side PRE_EXECUTION acquisition; references the same canonical boundary. */
    public record CrossProviderAcquireTarget(
            CrossProviderArtifactBoundary boundary) implements Target {

        public CrossProviderAcquireTarget {
            Objects.requireNonNull(boundary, "boundary");
        }

        @Override
        public ExecutionStepId memberUnitId() {
            return boundary.consumerUnitId();
        }
    }
}
