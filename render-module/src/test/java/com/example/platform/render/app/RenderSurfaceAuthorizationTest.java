package com.example.platform.render.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.identity.api.authorization.AuthorizationDeniedException;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.shared.authorization.AuthorizationResourceType;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.web.TenantContext;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * AUTH-UNPROTECTED-FIX-001 — the render-surface authorization boundary is fail-closed and
 * project/tenant scoped: it consults the canonical port with the addressed scope and denies on
 * tenant mismatch, missing actor, blank scope, or an RBAC deny.
 */
class RenderSurfaceAuthorizationTest {

    private static final String TENANT = "tenant-1";
    private static final String OTHER_TENANT = "tenant-2";
    private static final String PROJECT_A = "prj_a";
    private static final String PROJECT_B = "prj_b";

    private RenderSurfaceAuthorization authorization;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        authorization = new RenderSurfaceAuthorization(port(), actorResolver());
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void projectReadAllowedForAuthorizedProject() {
        authorization.requireProjectRead(TENANT, PROJECT_A);
    }

    @Test
    void projectReadDeniedForOtherProject() {
        assertThrows(AuthorizationDeniedException.class,
                () -> authorization.requireProjectRead(TENANT, PROJECT_B));
    }

    @Test
    void projectWriteDeniedForOtherProject() {
        assertThrows(AuthorizationDeniedException.class,
                () -> authorization.requireProjectWrite(TENANT, PROJECT_B));
    }

    @Test
    void blankProjectFailsClosed() {
        assertThrows(ResponseStatusException.class,
                () -> authorization.requireProjectRead(TENANT, " "));
    }

    @Test
    void explicitTenantMustEqualAmbientTenant() {
        assertThrows(ResponseStatusException.class,
                () -> authorization.requireProjectRead(OTHER_TENANT, PROJECT_A));
    }

    @Test
    void missingActorIsUnauthorized() {
        RenderSurfaceAuthorization noActor = new RenderSurfaceAuthorization(port(), Optional::empty);
        ResponseStatusException denied = assertThrows(ResponseStatusException.class,
                () -> noActor.requireProjectRead(TENANT, PROJECT_A));
        assertEquals(401, denied.getStatusCode().value());
    }

    @Test
    void tenantScopeReadAndWriteAreAuthorized() {
        authorization.requireTenantRead(TENANT);
        authorization.requireTenantWrite(TENANT);
    }

    // ── collaborators ───────────────────────────────────────────────────────

    private AuthorizationDecisionPort port() {
        return (AuthorizationRequest request) -> {
            boolean tenantScope = request.resource().resourceType() == AuthorizationResourceType.TENANT
                    && TENANT.equals(request.resource().tenantId());
            boolean projectScope = PROJECT_A.equals(request.resource().projectId())
                    && TENANT.equals(request.resource().tenantId());
            return tenantScope || projectScope
                    ? AuthorizationDecision.allow("TEST_ALLOW")
                    : AuthorizationDecision.deny("TEST_DENY", "TEST", "scope not authorized");
        };
    }

    private CanonicalActorResolver actorResolver() {
        return () -> Optional.of(CanonicalActor.user("user-1", TENANT, Set.of("EDITOR"), "test"));
    }
}
