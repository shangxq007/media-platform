package com.example.platform.identity.app;

import com.example.platform.identity.api.authorization.*;
import com.example.platform.identity.domain.Project;
import com.example.platform.shared.authorization.*;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.shared.web.PlatformException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectReadAuthorizationTest {
    final ProjectRepository projects = mock(ProjectRepository.class);
    final CanonicalActorResolver actors = mock(CanonicalActorResolver.class);
    final AuthorizationDecisionPort authorization = mock(AuthorizationDecisionPort.class, CALLS_REAL_METHODS);
    final TenantProjectService service = new TenantProjectService(mock(TenantRepository.class), projects,
            mock(UserRepository.class), mock(IdentityAccessService.class), actors, authorization, org.mockito.Mockito.mock(com.example.platform.identity.api.workspace.WorkspaceQueries.class));
    final CanonicalActor actor = CanonicalActor.user("reader", "tenant", Set.of(), "test");
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void missingActorOrAmbientContextCannotReachProjectStorage() {
        when(actors.resolveCurrentActor()).thenReturn(Optional.empty());
        assertThrows(PlatformException.class, () -> service.listProjects("tenant"));
        when(actors.resolveCurrentActor()).thenReturn(Optional.of(actor));
        assertThrows(AuthorizationDeniedException.class, () -> service.listProjects("tenant"));
        TenantContext.set("different");
        assertThrows(AuthorizationDeniedException.class, () -> service.getProject("tenant", "project"));
        verifyNoInteractions(projects, authorization);
    }
    @Test void discoveryFiltersEachProjectAndResolutionDoesNotReuseAnEarlierDecision() {
        TenantContext.set("tenant"); when(actors.resolveCurrentActor()).thenReturn(Optional.of(actor));
        var readable = new Project("allowed", "tenant", "Readable", "", Project.ProjectStatus.ACTIVE, Instant.EPOCH);
        var hidden = new Project("denied", "tenant", "Hidden", "", Project.ProjectStatus.ACTIVE, Instant.EPOCH);
        when(projects.findByTenantId("tenant")).thenReturn(List.of(readable, hidden));
        when(projects.findByIdAndTenant("allowed", "tenant")).thenReturn(Optional.of(readable));
        when(authorization.decide(any())).thenAnswer(invocation -> {
            AuthorizationRequest request = invocation.getArgument(0);
            assertEquals("READ", request.action().permissionKey());
            assertEquals(AuthorizationResourceType.PROJECT, request.resource().resourceType());
            return request.resource().resourceId().equals("allowed") ? AuthorizationDecision.allow("test-policy") : AuthorizationDecision.deny("DENIED", "test-policy");
        });
        assertEquals(List.of("allowed"), service.listProjects("tenant").stream().map(p -> p.id()).toList());
        assertEquals("allowed", service.getProject("tenant", "allowed").id());
        doReturn(AuthorizationDecision.deny("REVOKED", "test-policy")).when(authorization).decide(any());
        assertThrows(AuthorizationDeniedException.class, () -> service.getProject("tenant", "allowed"));
    }
}
