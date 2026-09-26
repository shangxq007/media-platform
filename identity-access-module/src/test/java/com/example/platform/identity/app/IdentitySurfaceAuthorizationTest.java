package com.example.platform.identity.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.identity.api.authorization.AuthorizationDeniedException;
import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.shared.authorization.AuthorizationResourceType;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * AUTH-UNPROTECTED-FIX-001 — the identity-surface authorization boundary is fail-closed and
 * project/tenant scoped.
 */
class IdentitySurfaceAuthorizationTest {

    private static final String TENANT = "tenant-1";
    private static final String OTHER_TENANT = "tenant-2";
    private static final String PROJECT_A = "prj_a";
    private static final String PROJECT_B = "prj_b";

    private IdentitySurfaceAuthorization authorization;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        authorization = new IdentitySurfaceAuthorization(
                port(),
                () -> Optional.of(CanonicalActor.user("user-1", TENANT, Set.of("ADMIN"), "test")));
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
    void tenantReadAllowed() {
        authorization.requireTenantRead(TENANT);
    }

    @Test
    void explicitTenantMustEqualAmbientTenant() {
        assertThrows(ResponseStatusException.class,
                () -> authorization.requireProjectRead(OTHER_TENANT, PROJECT_A));
    }

    @Test
    void missingActorIsUnauthorized() {
        IdentitySurfaceAuthorization noActor =
                new IdentitySurfaceAuthorization(port(), Optional::empty);
        ResponseStatusException denied = assertThrows(ResponseStatusException.class,
                () -> noActor.requireTenantRead(TENANT));
        assertEquals(401, denied.getStatusCode().value());
    }

    private AuthorizationDecisionPort port() {
        return request -> {
            boolean tenantScope = request.resource().resourceType() == AuthorizationResourceType.TENANT
                    && TENANT.equals(request.resource().tenantId());
            boolean projectScope = PROJECT_A.equals(request.resource().projectId())
                    && TENANT.equals(request.resource().tenantId());
            return tenantScope || projectScope
                    ? AuthorizationDecision.allow("TEST_ALLOW")
                    : AuthorizationDecision.deny("TEST_DENY", "TEST", "scope not authorized");
        };
    }
}
