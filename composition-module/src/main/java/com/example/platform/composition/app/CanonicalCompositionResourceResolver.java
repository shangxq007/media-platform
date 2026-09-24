package com.example.platform.composition.app;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.app.ArtifactWorkspaceAccessQuery;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.composition.domain.CompositionModels.TemplateWorkflow;
import com.example.platform.shared.identity.ArtifactId;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/** Resolves Composition assets through the canonical Artifact authority. */
@Component
public final class CanonicalCompositionResourceResolver implements CompositionResourceResolver {
    private final ArtifactQueryService artifacts;
    private final ArtifactWorkspaceAccessQuery workspaceArtifacts;
    @Autowired public CanonicalCompositionResourceResolver(ArtifactWorkspaceAccessQuery workspaceArtifacts) { this.workspaceArtifacts = Objects.requireNonNull(workspaceArtifacts); this.artifacts = null; }
    /** Test-only compatibility constructor; production uses the owner-provided workspace query. */
    public CanonicalCompositionResourceResolver(ArtifactQueryService artifacts) { this.artifacts = Objects.requireNonNull(artifacts); this.workspaceArtifacts = null; }

    @Override public ResourceResolution resolve(TemplateWorkflow workflow, String tenantId, String workspaceId) {
        if (!Objects.equals(workflow.tenantId(), tenantId) || !Objects.equals(workflow.workspaceId(), workspaceId))
            throw new IllegalArgumentException("resource scope does not match authenticated scope");
        Set<String> requirements = new LinkedHashSet<>(workflow.requiredAssets());
        workflow.steps().forEach(step -> requirements.addAll(step.requiredAssets()));
        Set<String> available = new LinkedHashSet<>();
        for (String requirement : requirements) {
            String[] parts = requirement.split("\\|", -1);
            String id = parts.length == 3 ? parts[2] : requirement;
            Artifact artifact = (workspaceArtifacts == null ? artifacts.getArtifact(tenantId, new ArtifactId(id)) : workspaceArtifacts.findArtifact(tenantId, workspaceId, new ArtifactId(id))).orElseThrow(
                    () -> new IllegalArgumentException("required asset is unavailable: " + requirement));
            if (artifact.state() != ArtifactState.AVAILABLE) throw new IllegalArgumentException("required asset is inactive: " + requirement);
            if (parts.length == 3) {
                try {
                    if (artifact.artifactKind() != ArtifactKind.valueOf(parts[0]) || artifact.mediaType() != ArtifactMediaType.valueOf(parts[1]))
                        throw new IllegalArgumentException("required asset kind/media does not match: " + requirement);
                } catch (IllegalArgumentException e) { throw new IllegalArgumentException("malformed or mismatched asset: " + requirement, e); }
            }
            available.add(requirement);
        }
        return new ResourceResolution(Set.copyOf(available), List.copyOf(requirements));
    }
}
