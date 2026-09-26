package com.example.platform.web.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.identity.api.authorization.AuthorizationDeniedException;
import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * AUTH-UNPROTECTED-FIX-001 — {@code /api/artifacts/{artifactId}} already bound the artifact to the
 * supplied workspace, but never checked the caller's RBAC standing. The canonical port is now
 * consulted (workspace-scoped READ) and a denial fails closed.
 */
class ArtifactRetrievalAuthorizationTest {

    private static final String TENANT = "tenant-1";
    private static final String WORKSPACE = "ws_1";

    private ArtifactQueryService artifacts;
    private ArtifactRetrievalController controller;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        artifacts = mock(ArtifactQueryService.class);
        when(artifacts.isAuthorizedWorkspace(eq(TENANT), any(), any())).thenReturn(true);
        when(artifacts.getArtifact(eq(TENANT), any())).thenReturn(Optional.empty());
        AuthorizationDecisionPort port = (AuthorizationRequest request) ->
                WORKSPACE.equals(request.resource().resourceId())
                        ? AuthorizationDecision.allow("TEST_ALLOW")
                        : AuthorizationDecision.deny("TEST_DENY", "TEST", "workspace not authorized");
        controller = new ArtifactRetrievalController(
                artifacts,
                port,
                () -> Optional.of(CanonicalActor.user("user-1", TENANT, Set.of("VIEWER"), "test")));
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void authorizedWorkspaceClearsTheGate() {
        // The artifact lookup returns empty, so the observable result is 404 (not a denial).
        assertEquals(404, controller.get("art_1", WORKSPACE).getStatusCode().value());
    }

    @Test
    void rbACDenialFailsClosed() {
        assertThrows(AuthorizationDeniedException.class, () -> controller.get("art_1", "ws_denied"));
    }

    @Test
    void missingTenantIsUnauthorized() {
        TenantContext.clear();
        assertEquals(401, controller.get("art_1", WORKSPACE).getStatusCode().value());
    }

    @Test
    void foreignTenantActorIsForbidden() {
        ArtifactRetrievalController foreign = new ArtifactRetrievalController(
                artifacts,
                request -> AuthorizationDecision.allow("TEST_ALLOW"),
                () -> Optional.of(CanonicalActor.user("user-2", "tenant-2", Set.of("VIEWER"), "test")));
        assertThrows(ResponseStatusException.class, () -> foreign.get("art_1", WORKSPACE));
    }
}
