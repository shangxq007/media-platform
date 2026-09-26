package com.example.platform.web.artifact;

import com.example.platform.artifact.app.ArtifactProjectAuthorizationPort;
import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.shared.authorization.AuthorizableResourceRef;
import com.example.platform.shared.authorization.AuthorizationAction;
import com.example.platform.shared.authorization.AuthorizationContext;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.authorization.AuthorizationResourceType;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Composition-root implementation of the owner-published
 * {@link ArtifactProjectAuthorizationPort}.
 *
 * <p>Mirrors {@link com.example.platform.web.render.TimelineProjectAuthorizationService}:
 * the canonical Identity {@link AuthorizationDecisionPort} is the single decision
 * path (ARTIFACT_AUTHORITY_CONTRACT_V1; no second RBAC authority). Fail-closed order:
 * explicit tenant required, explicit == ambient tenant, an authenticated actor is
 * required, actor tenant == explicit tenant, then the project-scoped RBAC decision
 * for the Artifact action.</p>
 */
@Component
public final class WebArtifactProjectAuthorization implements ArtifactProjectAuthorizationPort {

    private static final AuthorizationAction READ = new AuthorizationAction(
            "artifact.read", AuthorizationResourceType.PROJECT, "Read canonical Artifact");
    private static final AuthorizationAction WRITE = new AuthorizationAction(
            "artifact.lifecycle.manage", AuthorizationResourceType.PROJECT,
            "Manage canonical Artifact lifecycle");

    private final AuthorizationDecisionPort authorizationPort;
    private final CanonicalActorResolver actorResolver;

    public WebArtifactProjectAuthorization(
            AuthorizationDecisionPort authorizationPort,
            CanonicalActorResolver actorResolver) {
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort");
        this.actorResolver = Objects.requireNonNull(actorResolver, "actorResolver");
    }

    @Override
    public void requireRead(String tenantId, String projectId) {
        authorize(tenantId, projectId, READ);
    }

    @Override
    public void requireWrite(String tenantId, String projectId) {
        authorize(tenantId, projectId, WRITE);
    }

    private void authorize(String explicitTenantId, String projectId, AuthorizationAction action) {
        requireText(explicitTenantId, "tenantId");
        requireText(projectId, "projectId");
        String ambientTenantId = TenantContext.get();
        if (!explicitTenantId.equals(ambientTenantId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "explicit and ambient tenant must match");
        }
        CanonicalActor actor = actorResolver.resolveCurrentActor()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "authenticated actor required"));
        if (!explicitTenantId.equals(actor.tenantId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "authenticated actor tenant must match request tenant");
        }
        authorizationPort.requireAuthorized(new AuthorizationRequest(
                actor,
                action,
                new AuthorizableResourceRef(
                        AuthorizationResourceType.PROJECT,
                        projectId,
                        explicitTenantId,
                        projectId,
                        null),
                new AuthorizationContext(
                        "artifact-http", null,
                        Map.of("canonicalAuthorSource", "authenticated-actor"))));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, field + " required");
        }
    }
}
