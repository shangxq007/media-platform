package com.example.platform.render.domain.renderplan;

import com.example.platform.extension.domain.CapabilityRequirement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A typed render node (C3/C6). Declarative semantic step: kind, component path,
 * operation key, artifact references, capability/output requirements, typed
 * materialization requirements (ROADMAP20 correction F1 — the materialized
 * logical WHAT), and an exact sample window where relevant (DECODE nodes).
 * Capability requirements use the platform capability authority (ROADMAP_16,
 * correction F3). No Map&lt;String,Object&gt;; no provider/worker/device/tier/price fields.
 */
public record RenderNode(
        RenderNodeId id,
        RenderNodeKind kind,
        RenderComponentPath componentPath,
        String operationKey,
        List<RenderArtifactReference> artifactReferences,
        List<CapabilityRequirement> capabilityRequirements,
        List<RenderOutputRequirement> outputRequirements,
        List<RenderExecutionRequirement> executionRequirements,
        List<RenderMaterializationRequirement> materializationRequirements,
        Optional<RenderSampleWindow> requiredSampleWindow,
        RenderExecutionCoverage executionCoverage) {

    public RenderNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(componentPath, "componentPath");
        Objects.requireNonNull(operationKey, "operationKey");
        if (operationKey.isBlank()) {
            throw new IllegalArgumentException("operationKey must not be blank");
        }
        artifactReferences = artifactReferences != null ? List.copyOf(artifactReferences) : List.of();
        capabilityRequirements = capabilityRequirements != null ? List.copyOf(capabilityRequirements) : List.of();
        outputRequirements = outputRequirements != null ? List.copyOf(outputRequirements) : List.of();
        executionRequirements = executionRequirements != null ? List.copyOf(executionRequirements) : List.of();
        materializationRequirements = materializationRequirements != null
                ? List.copyOf(materializationRequirements) : List.of();
        requiredSampleWindow = requiredSampleWindow != null ? requiredSampleWindow : Optional.empty();
        // executionCoverage nullable: null = no single coverage interval


        // (C12/C13 correction — timeline-coordinate contribution; distinct
        // from RenderSampleWindow source-coordinate sampling)
    }
}
