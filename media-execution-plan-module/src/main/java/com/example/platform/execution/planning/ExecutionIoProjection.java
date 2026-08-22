package com.example.platform.execution.planning;

import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.render.domain.renderplan.RenderArtifactReference;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement;
import com.example.platform.render.domain.renderplan.RenderMaterializationRequirement;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderOutputRequirement;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import java.util.List;
import java.util.Objects;

/**
 * Roadmap #21 typed IO binding projection (Blocker B).
 *
 * <p>Derived typed immutable references over exact #20 declarations. NO
 * independent media-type authority, NO mutable artifact AVAILABLE-state, NO
 * ExecutionInputRole / ExecutionOutputRole, NO storage URI authority, NO
 * expectedProperties / retentionClass shadow. The typed #20 declaration is
 * the semantic carrier; this projection only binds it to a logical node.
 */
public final class ExecutionIoProjection {

    private ExecutionIoProjection() {
    }

    /**
     * Typed input binding: which producer node + which declared artifact the
     * consuming node depends on, with the exact #20 dependency semantics and
     * the exact required sample window.
     */
    public record InputBinding(
            String logicalNodeId,
            RenderNodeId sourceRenderNodeId,
            String producerLogicalNodeId,
            RenderNodeId producerRenderNodeId,
            com.example.platform.render.domain.renderplan.RenderDependency dependencyVariant,
            RenderArtifactReference artifactReference,
            RenderSampleWindow requiredSampleWindow) {

        public InputBinding {
            Objects.requireNonNull(logicalNodeId, "logicalNodeId");
            Objects.requireNonNull(sourceRenderNodeId, "sourceRenderNodeId");
            Objects.requireNonNull(dependencyVariant, "dependencyVariant — exact RenderDependency required");
        }
    }

    /**
     * Typed output declaration: exact #20 output/materialization requirements
     * as declared on the producing node. Never re-declared, never shadowed.
     */
    public record OutputDeclaration(
            String logicalNodeId,
            RenderNodeId sourceRenderNodeId,
            List<RenderOutputRequirement> outputRequirements,
            List<RenderMaterializationRequirement> materializationRequirements,
            List<RenderArtifactReference> artifactReferences) {

        public OutputDeclaration {
            Objects.requireNonNull(logicalNodeId, "logicalNodeId");
            Objects.requireNonNull(sourceRenderNodeId, "sourceRenderNodeId");
            outputRequirements = outputRequirements == null ? List.of() : List.copyOf(outputRequirements);
            materializationRequirements = materializationRequirements == null
                    ? List.of() : List.copyOf(materializationRequirements);
            artifactReferences = artifactReferences == null ? List.of() : List.copyOf(artifactReferences);
        }
    }

    /** Typed capability requirement reference — full CapabilityRequirement semantics, no downgrade. */
    public record CapabilityRequirementRef(CapabilityRequirement declaration) {
        public CapabilityRequirementRef {
            Objects.requireNonNull(declaration, "declaration");
        }
    }

    /** Typed execution-intent reference — 1:1 derived, never redefined. */
    public record ExecutionIntentRef(RenderExecutionRequirement declaration) {
        public ExecutionIntentRef {
            Objects.requireNonNull(declaration, "declaration");
        }
    }
}
