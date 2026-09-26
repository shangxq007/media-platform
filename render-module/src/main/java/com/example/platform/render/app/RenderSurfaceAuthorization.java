package com.example.platform.render.app;

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
 * AUTH-UNPROTECTED-FIX-001 — fail-closed project/tenant authorization boundary for the
 * render-module HTTP surfaces that previously had none beyond {@code authenticated()}.
 *
 * <p>This is the render-module analogue of
 * {@code com.example.platform.web.render.TimelineProjectAuthorizationService}: it reaches the single
 * canonical Identity {@link AuthorizationDecisionPort} (AR-AUTH-003/004/005 — never
 * entitlement/flag/capability) and completes authorization BEFORE the caller hydrates or
 * discloses render data. The returned actor is the only canonical author source.</p>
 *
 * <p>Fail-closed order: explicit tenant required, explicit == ambient tenant, an authenticated
 * actor is required, actor tenant == explicit tenant, then the scoped RBAC decision for the
 * render action (READ/WRITE, PROJECT or TENANT resource).</p>
 */
@Component
public final class RenderSurfaceAuthorization {

    private static final AuthorizationAction PROJECT_READ = new AuthorizationAction(
            "READ", AuthorizationResourceType.PROJECT, "Read project-scoped render data");
    private static final AuthorizationAction PROJECT_WRITE = new AuthorizationAction(
            "WRITE", AuthorizationResourceType.PROJECT, "Mutate project-scoped render data");
    private static final AuthorizationAction TENANT_READ = new AuthorizationAction(
            "READ", AuthorizationResourceType.TENANT, "Read tenant-scoped render data");
    private static final AuthorizationAction TENANT_WRITE = new AuthorizationAction(
            "WRITE", AuthorizationResourceType.TENANT, "Mutate tenant-scoped render data");

    private final AuthorizationDecisionPort authorizationPort;
    private final CanonicalActorResolver actorResolver;

    public RenderSurfaceAuthorization(
            AuthorizationDecisionPort authorizationPort,
            CanonicalActorResolver actorResolver) {
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort");
        this.actorResolver = Objects.requireNonNull(actorResolver, "actorResolver");
    }

    public CanonicalActor requireProjectRead(String tenantId, String projectId) {
        return requireProject(tenantId, projectId, PROJECT_READ);
    }

    public CanonicalActor requireProjectWrite(String tenantId, String projectId) {
        return requireProject(tenantId, projectId, PROJECT_WRITE);
    }

    public CanonicalActor requireTenantRead(String tenantId) {
        return requireTenant(tenantId, TENANT_READ);
    }

    public CanonicalActor requireTenantWrite(String tenantId) {
        return requireTenant(tenantId, TENANT_WRITE);
    }

    private CanonicalActor requireProject(
            String explicitTenantId, String projectId, AuthorizationAction action) {
        requireText(explicitTenantId, "tenantId");
        requireText(projectId, "projectId");
        CanonicalActor actor = requireActor(explicitTenantId);
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
                        "render-surface", null,
                        Map.of("canonicalAuthorSource", "authenticated-actor"))));
        return actor;
    }

    private CanonicalActor requireTenant(String explicitTenantId, AuthorizationAction action) {
        requireText(explicitTenantId, "tenantId");
        CanonicalActor actor = requireActor(explicitTenantId);
        authorizationPort.requireAuthorized(new AuthorizationRequest(
                actor,
                action,
                new AuthorizableResourceRef(
                        AuthorizationResourceType.TENANT,
                        explicitTenantId,
                        explicitTenantId,
                        null,
                        null),
                new AuthorizationContext(
                        "render-surface", null,
                        Map.of("canonicalAuthorSource", "authenticated-actor"))));
        return actor;
    }

    private CanonicalActor requireActor(String explicitTenantId) {
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
        return actor;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, field + " required");
        }
    }
}
