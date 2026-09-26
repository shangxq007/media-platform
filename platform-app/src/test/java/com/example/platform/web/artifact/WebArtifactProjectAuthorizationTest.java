package com.example.platform.web.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.authorization.AuthorizationResourceType;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

/** Fail-closed and project-scope contract of the Artifact authorization adapter. */
class WebArtifactProjectAuthorizationTest {

    private final AuthorizationDecisionPort port = mock(AuthorizationDecisionPort.class);
    private final CanonicalActorResolver actors = mock(CanonicalActorResolver.class);
    private final WebArtifactProjectAuthorization authorization =
            new WebArtifactProjectAuthorization(port, actors);
    private final CanonicalActor actor = CanonicalActor.user("user-1", "tenant-a", Set.of(), "test");

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void rejectsBlankTenantOrProjectBeforeAnyDecision() {
        TenantContext.set("tenant-a");
        when(actors.resolveCurrentActor()).thenReturn(Optional.of(actor));

        assertThrows(ResponseStatusException.class, () -> authorization.requireRead(" ", "project-a"));
        assertThrows(ResponseStatusException.class, () -> authorization.requireRead("tenant-a", " "));
        verifyNoInteractions(port);
    }

    @Test
    void rejectsAmbientTenantMismatch() {
        TenantContext.set("tenant-b");
        when(actors.resolveCurrentActor()).thenReturn(Optional.of(actor));

        assertThrows(ResponseStatusException.class,
                () -> authorization.requireRead("tenant-a", "project-a"));
        verifyNoInteractions(port);
    }

    @Test
    void requiresAnAuthenticatedActor() {
        TenantContext.set("tenant-a");
        when(actors.resolveCurrentActor()).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> authorization.requireWrite("tenant-a", "project-a"));
        verifyNoInteractions(port);
    }

    @Test
    void rejectsActorTenantMismatch() {
        TenantContext.set("tenant-a");
        when(actors.resolveCurrentActor())
                .thenReturn(Optional.of(CanonicalActor.user("user-1", "tenant-x", Set.of(), "test")));

        assertThrows(ResponseStatusException.class,
                () -> authorization.requireRead("tenant-a", "project-a"));
        verifyNoInteractions(port);
    }

    @Test
    void delegatesProjectScopedArtifactActionsToTheCanonicalPort() {
        TenantContext.set("tenant-a");
        when(actors.resolveCurrentActor()).thenReturn(Optional.of(actor));

        authorization.requireRead("tenant-a", "project-a");
        authorization.requireWrite("tenant-a", "project-a");

        ArgumentCaptor<AuthorizationRequest> requests = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(port, times(2)).requireAuthorized(requests.capture());
        assertEquals("artifact.read", requests.getAllValues().get(0).action().permissionKey());
        assertEquals("artifact.lifecycle.manage", requests.getAllValues().get(1).action().permissionKey());
        for (AuthorizationRequest request : requests.getAllValues()) {
            assertEquals(AuthorizationResourceType.PROJECT, request.resource().resourceType());
            assertEquals("project-a", request.resource().projectId());
            assertEquals("tenant-a", request.resource().tenantId());
            assertEquals(actor, request.actor());
        }
    }
}
