package com.example.platform.web.artifact;

import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantContext;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The sole public artifact retrieval boundary. */
@RestController
@RequestMapping("/api/artifacts")
public final class ArtifactRetrievalController {
    private final ArtifactQueryService artifacts;

    public ArtifactRetrievalController(ArtifactQueryService artifacts) {
        this.artifacts = artifacts;
    }

    @GetMapping("/{artifactId}")
    public ResponseEntity<?> get(@PathVariable String artifactId, @RequestParam String workspaceId) {
        String tenant = TenantContext.get();
        if (tenant == null || tenant.isBlank()) return ResponseEntity.status(401).build();
        if (!artifacts.isAuthorizedWorkspace(tenant, workspaceId, new ArtifactId(artifactId))) return ResponseEntity.notFound().build();
        return artifacts.getArtifact(tenant, new ArtifactId(artifactId))
                .filter(a -> a.contentDigest() != null && a.contentDigest().canonicalValue() != null
                        && !a.contentDigest().canonicalValue().isBlank() && a.byteLength() >= 0)
                .<ResponseEntity<?>>map(a -> ResponseEntity.ok(Map.of(
                        "artifactId", a.artifactId().value(), "tenantId", a.tenantId(),
                        "contentDigest", a.contentDigest().canonicalValue(), "byteLength", a.byteLength(),
                        "mediaType", a.mediaType().name(), "kind", a.artifactKind().name(),
                        "state", a.state().name(), "schemaVersion", a.schemaVersion(), "createdAt", a.createdAt())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{artifactId}/lineage")
    public ResponseEntity<?> lineage(@PathVariable String artifactId, @RequestParam String workspaceId) {
        String tenant = TenantContext.get();
        if (tenant == null || tenant.isBlank()) return ResponseEntity.status(401).build();
        var id = new ArtifactId(artifactId);
        if (!artifacts.isAuthorizedWorkspace(tenant, workspaceId, id) || artifacts.getArtifact(tenant, id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("artifactId", artifactId,
                "workspaceId", workspaceId,
                "parents", artifacts.listParents(tenant, id).stream().map(ArtifactId::value).toList(),
                "children", artifacts.listChildren(tenant, id).stream().map(ArtifactId::value).toList()));
    }
}
