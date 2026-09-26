package com.example.platform.web.artifact;

import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.shared.authorization.AuthorizableResourceRef;
import com.example.platform.shared.authorization.AuthorizationAction;
import com.example.platform.shared.authorization.AuthorizationContext;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.authorization.AuthorizationResourceType;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantContext;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The sole public artifact retrieval boundary. */
@RestController
@RequestMapping("/api/artifacts")
public final class ArtifactRetrievalController {
    private static final AuthorizationAction WORKSPACE_READ = new AuthorizationAction(
            "READ", AuthorizationResourceType.WORKSPACE, "Read artifact workspace");

    private final ArtifactQueryService artifacts;
    private final AuthorizationDecisionPort authorization;
    private final CanonicalActorResolver actorResolver;

    public ArtifactRetrievalController(ArtifactQueryService artifacts,
            AuthorizationDecisionPort authorization, CanonicalActorResolver actorResolver) {
        this.artifacts = artifacts;
        this.authorization = authorization;
        this.actorResolver = actorResolver;
    }

    /**
     * AUTH-UNPROTECTED-FIX-001: the artifact is bound to the supplied workspace, but the caller's
     * RBAC standing in that workspace was never consulted. Fail closed through the canonical port.
     */
    private void requireWorkspaceRead(String tenant, String workspaceId) {
        CanonicalActor actor = actorResolver.resolveCurrentActor()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "authenticated actor required"));
        if (!tenant.equals(actor.tenantId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "authenticated actor tenant must match request tenant");
        }
        authorization.requireAuthorized(new AuthorizationRequest(
                actor,
                WORKSPACE_READ,
                new AuthorizableResourceRef(
                        AuthorizationResourceType.WORKSPACE,
                        workspaceId,
                        tenant,
                        null,
                        workspaceId),
                new AuthorizationContext(
                        "artifact-retrieval", workspaceId,
                        Map.of("canonicalAuthorSource", "authenticated-actor"))));
    }

    @GetMapping("/{artifactId}")
    public ResponseEntity<?> get(@PathVariable String artifactId, @RequestParam String workspaceId) {
        String tenant = TenantContext.get();
        if (tenant == null || tenant.isBlank()) return ResponseEntity.status(401).build();
        if (!artifacts.isAuthorizedWorkspace(tenant, workspaceId, new ArtifactId(artifactId))) return ResponseEntity.notFound().build();
        requireWorkspaceRead(tenant, workspaceId);
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
        requireWorkspaceRead(tenant, workspaceId);
        return ResponseEntity.ok(Map.of("artifactId", artifactId,
                "workspaceId", workspaceId,
                "parents", artifacts.listParents(tenant, id).stream().map(ArtifactId::value).toList(),
                "children", artifacts.listChildren(tenant, id).stream().map(ArtifactId::value).toList()));
    }
}
