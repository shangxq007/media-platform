package com.example.platform.identity.app;

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
 * identity-access HTTP surfaces that previously had none beyond {@code authenticated()}.
 *
 * <p>Reaches the single canonical Identity {@link AuthorizationDecisionPort} (AR-AUTH-003/004/005)
 * and completes authorization before the caller hydrates or discloses project/tenant data. The
 * returned actor is the only canonical author source.</p>
 *
 * <p>Fail-closed order: explicit tenant required, explicit == ambient tenant, an authenticated
 * actor is required, actor tenant == explicit tenant, then the scoped RBAC decision.</p>
 */
@Component
public final class IdentitySurfaceAuthorization {

    private static final AuthorizationAction PROJECT_READ = new AuthorizationAction(
            "READ", AuthorizationResourceType.PROJECT, "Read project-scoped identity data");
    private static final AuthorizationAction TENANT_READ = new AuthorizationAction(
            "READ", AuthorizationResourceType.TENANT, "Read tenant-scoped identity data");

    private final AuthorizationDecisionPort authorizationPort;
    private final CanonicalActorResolver actorResolver;

    public IdentitySurfaceAuthorization(
            AuthorizationDecisionPort authorizationPort,
            CanonicalActorResolver actorResolver) {
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort");
        this.actorResolver = Objects.requireNonNull(actorResolver, "actorResolver");
    }

    public CanonicalActor requireProjectRead(String tenantId, String projectId) {
        requireText(tenantId, "tenantId");
        requireText(projectId, "projectId");
        CanonicalActor actor = requireActor(tenantId);
        authorizationPort.requireAuthorized(new AuthorizationRequest(
                actor,
                PROJECT_READ,
                new AuthorizableResourceRef(
                        AuthorizationResourceType.PROJECT,
                        projectId,
                        tenantId,
                        projectId,
                        null),
                new AuthorizationContext(
                        "identity-surface", null,
                        Map.of("canonicalAuthorSource", "authenticated-actor"))));
        return actor;
    }

    public CanonicalActor requireTenantRead(String tenantId) {
        requireText(tenantId, "tenantId");
        CanonicalActor actor = requireActor(tenantId);
        authorizationPort.requireAuthorized(new AuthorizationRequest(
                actor,
                TENANT_READ,
                new AuthorizableResourceRef(
                        AuthorizationResourceType.TENANT,
                        tenantId,
                        tenantId,
                        null,
                        null),
                new AuthorizationContext(
                        "identity-surface", null,
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
