package com.example.platform.render.app;

import com.example.platform.identity.api.authorization.*;
import com.example.platform.identity.api.project.ProjectReadQuery;
import com.example.platform.identity.api.dto.ProjectResponse;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.policy.RenderPolicyEngine;
import com.example.platform.shared.authorization.*;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.shared.web.PlatformException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RenderJobReadAuthorizationTest {
    final RenderJobRepository jobs = mock(RenderJobRepository.class);
    final ProjectReadQuery projects = mock(ProjectReadQuery.class);
    final CanonicalActorResolver actors = mock(CanonicalActorResolver.class);
    final AuthorizationDecisionPort authorization = mock(AuthorizationDecisionPort.class, CALLS_REAL_METHODS);
    final RenderJobService service = new RenderJobService(jobs, mock(RenderPolicyEngine.class), mock(com.example.platform.render.app.event.RenderLifecyclePublisher.class), mock(RenderJobStatusHistoryRepository.class), null, projects, actors, authorization, org.mockito.Mockito.mock(com.example.platform.render.app.RenderAcceptanceContextService.class));
    @AfterEach void clear() { TenantContext.clear(); }
    void context() {
        TenantContext.set("tenant");
        when(actors.resolveCurrentActor()).thenReturn(Optional.of(CanonicalActor.user("reader", "tenant", Set.of(), "test")));
        when(projects.getProject("tenant", "project")).thenReturn(new ProjectResponse("project", "tenant", "Project", "", "ACTIVE", Instant.EPOCH));
    }
    @Test void missingContextIsRejectedBeforeAnyProjectOrJobQuery() {
        when(actors.resolveCurrentActor()).thenReturn(Optional.empty());
        assertThrows(PlatformException.class, () -> service.listByProject("tenant", "project"));
        when(actors.resolveCurrentActor()).thenReturn(Optional.of(CanonicalActor.user("reader", "tenant", Set.of(), "test")));
        assertThrows(AuthorizationDeniedException.class, () -> service.listByProject("tenant", "project"));
        verifyNoInteractions(projects, jobs, authorization);
    }
    @Test void deniedJobDecisionPreventsDetailDisclosureAndFiltersInventory() {
        context();
        when(authorization.decide(any())).thenAnswer(invocation -> {
            AuthorizationRequest request = invocation.getArgument(0);
            assertEquals("READ", request.action().permissionKey());
            assertEquals(AuthorizationResourceType.RENDER_JOB, request.resource().resourceType());
            assertEquals("project", request.resource().projectId());
            return AuthorizationDecision.deny("JOB_DENIED", "test-policy", "Access denied");
        });
        assertThrows(AuthorizationDeniedException.class, () -> service.getByIdAndProject("tenant", "project", "job"));
        verifyNoInteractions(jobs);
        when(jobs.listByProjectAndTenant("project", "tenant")).thenReturn(List.of(new RenderJobResponse("job", "project", "snapshot", "profile", "FAILED")));
        assertEquals(List.of(), service.listByProject("tenant", "project"));
    }
    @Test void mismatchedOwnerScopeIsNotAcceptedAsAnAuthorizationShortcut() {
        context();
        when(projects.getProject("tenant", "project")).thenReturn(new ProjectResponse("project", "foreign", "Hidden", "", "ACTIVE", Instant.EPOCH));
        assertThrows(PlatformException.class, () -> service.listByProject("tenant", "project"));
        verifyNoInteractions(jobs, authorization);
    }
}
