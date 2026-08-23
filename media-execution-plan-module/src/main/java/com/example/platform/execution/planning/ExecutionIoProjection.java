package com.example.platform.execution.planning;

import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.render.domain.renderplan.RenderArtifactReference;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement;
import com.example.platform.render.domain.renderplan.RenderMaterializationRequirement;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderOutputRequirement;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import java.util.List;
import java.util.Objects;

/**
 * Roadmap #21 typed IO projection (Blocker B3) — semantic direction correct.
 *
 * <p>#20 RenderArtifactReference sealed variants are projected by semantic
 * direction:
 * <ul>
 *   <li>{@code SourceArtifact} (pinned immutable consumed input) → typed
 *       {@link InputBinding} — NEVER an output</li>
 *   <li>{@code IntermediateArtifactExpectation} (planned intermediate result)
 *       → typed {@link OutputDeclaration}</li>
 *   <li>{@code FinalArtifactExpectation} (planned final result) → typed
 *       {@link OutputDeclaration}</li>
 * </ul>
 *
 * <p>NO independent media-type authority, NO mutable AVAILABLE-state, NO
 * ExecutionInputRole / ExecutionOutputRole, NO storage URI authority, NO
 * expectedProperties / retentionClass shadow. Strong typed identities
 * (frozen ledger REUSE_AS_CANONICAL).
 */
public final class ExecutionIoProjection {

    private ExecutionIoProjection() {
    }

    /**
     * Typed input binding: typed identity (ExecutionInputId), consumer/producer
     * typed identities, exact #20 dependency variant, the exact typed
     * SourceArtifact reference (with content digest) when the upstream
     * semantics declare one, and the exact required sample window (source
     * coordinates).
     */
    public record InputBinding(
            ExecutionInputId inputId,
            String consumerLogicalNodeId,
            com.example.platform.execution.domain.ExecutionStepId consumerStepId,
            RenderNodeId consumerRenderNodeId,
            String producerLogicalNodeId,
            com.example.platform.execution.domain.ExecutionStepId producerStepId,
            RenderNodeId producerRenderNodeId,
            com.example.platform.render.domain.renderplan.RenderDependency dependencyVariant,
            RenderArtifactReference.SourceArtifact sourceArtifact,
            RenderSampleWindow requiredSampleWindow) {

        public InputBinding {
            Objects.requireNonNull(inputId, "inputId");
            Objects.requireNonNull(consumerLogicalNodeId, "consumerLogicalNodeId");
            // dependencyVariant null ONLY for root source-artifact inputs with
            // no producer edge (e.g. DECODE consuming pinned source media);
            // non-null everywhere else (exact RenderDependency variant)
            // sourceArtifact nullable only when upstream semantics declare no
            // source artifact for this dependency; never a silent null where a
            // SourceArtifact exists on the node
        }
    }

    /**
     * Typed output declaration: typed identity (ExecutionOutputId), exact #20
     * output/materialization requirements, and the exact typed artifact
     * expectations (Intermediate / Final). SourceArtifact never appears here.
     */
    public record OutputDeclaration(
            ExecutionOutputId outputId,
            String logicalNodeId,
            RenderNodeId sourceRenderNodeId,
            List<RenderOutputRequirement> outputRequirements,
            List<RenderMaterializationRequirement> materializationRequirements,
            List<RenderArtifactReference.IntermediateArtifactExpectation> intermediateArtifactExpectations,
            List<RenderArtifactReference.FinalArtifactExpectation> finalArtifactExpectations) {

        public OutputDeclaration {
            Objects.requireNonNull(outputId, "outputId");
            Objects.requireNonNull(logicalNodeId, "logicalNodeId");
            Objects.requireNonNull(sourceRenderNodeId, "sourceRenderNodeId");
            outputRequirements = PlanningCanonicalOrder.outputRequirements(outputRequirements);
            materializationRequirements = PlanningCanonicalOrder.materializations(materializationRequirements);
            intermediateArtifactExpectations =
                    PlanningCanonicalOrder.intermediateArtifacts(intermediateArtifactExpectations);
            finalArtifactExpectations = PlanningCanonicalOrder.finalArtifacts(finalArtifactExpectations);
        }
    }

    /** Typed capability requirement reference — full semantics, no downgrade. */
    public record CapabilityRequirementRef(com.example.platform.extension.domain.CapabilityRequirement declaration) {
        public CapabilityRequirementRef {
            Objects.requireNonNull(declaration, "declaration");
        }
    }

    /** Typed execution-intent reference — 1:1 derived. */
    public record ExecutionIntentRef(RenderExecutionRequirement declaration) {
        public ExecutionIntentRef {
            Objects.requireNonNull(declaration, "declaration");
        }
    }
}
