package com.example.platform.render.domain.renderplan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A typed render node (C3/C6). Declarative semantic step: kind, component path,
 * operation key, artifact references, capability/output requirements, and an
 * exact sample window where relevant (DECODE nodes). No Map<String,Object>; no
 * provider/worker/device/tier/price fields.
 */
public record RenderNode(
        RenderNodeId id,
        RenderNodeKind kind,
        RenderComponentPath componentPath,
        String operationKey,
        List<RenderArtifactReference> artifactReferences,
        List<RenderCapabilityRequirement> capabilityRequirements,
        List<RenderOutputRequirement> outputRequirements,
        List<RenderExecutionRequirement> executionRequirements,
        Optional<RenderSampleWindow> requiredSampleWindow) {

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
        requiredSampleWindow = requiredSampleWindow != null ? requiredSampleWindow : Optional.empty();
    }
}
