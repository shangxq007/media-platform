package com.example.platform.web;

import com.example.platform.auditcontract.api.AuditPort;
import com.example.platform.entitlement.app.EntitlementPolicyService;
import com.example.platform.entitlement.app.EntitlementService;
import com.example.platform.identity.app.PermissionService;
import com.example.platform.identity.app.ProjectRepository;
import com.example.platform.identity.app.TenantRepository;
import com.example.platform.policy.featureflag.FeatureFlagService;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MeControllerProjectsAuthorizationTest {

    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final MeController controller = new MeController(
            mock(TenantRepository.class), projects, mock(PermissionService.class),
            mock(EntitlementService.class), mock(EntitlementPolicyService.class),
            mock(FeatureFlagService.class), mock(AuditPort.class),
            mock(com.example.platform.web.collaboration.SharedResourceService.class));

    @BeforeEach
    void resetFixtures() {
        reset(projects);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearRequestState() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingAuthenticationIsUnauthorized() {
        assertEquals(401, controller.getProjects(0, 20).getStatusCode().value());
        verifyNoInteractions(projects);
    }

    @Test
    void authenticatedIdentityWithoutTenantScopeIsForbidden() {
        authenticate("user-a");
        assertEquals(403, controller.getProjects(0, 20).getStatusCode().value());
        verifyNoInteractions(projects);
    }

    @Test
    void authenticatedTenantScopeUsesOnlyServerContext() {
        authenticate("user-a");
        TenantContext.set("tenant-a");
        when(projects.findByTenantId("tenant-a")).thenReturn(List.of());

        var response = controller.getProjects(0, 20);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("projects", List.of(), "total", 0, "page", 0, "size", 20), response.getBody());
        verify(projects).findByTenantId("tenant-a");
        verify(projects, never()).findByTenantId("user-a");
    }

    @Test
    void requestContextsDoNotCrossBetweenThreads() throws Exception {
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> scopedResult("tenant-a"));
            var second = executor.submit(() -> scopedResult("tenant-b"));
            assertEquals("tenant-a", first.get());
            assertEquals("tenant-b", second.get());
        } finally {
            executor.shutdownNow();
        }
        verify(projects).findByTenantId("tenant-a");
        verify(projects).findByTenantId("tenant-b");
    }

    private String scopedResult(String tenant) {
        try {
            authenticate(tenant + "-user");
            TenantContext.set(tenant);
            when(projects.findByTenantId(tenant)).thenReturn(List.of());
            controller.getProjects(0, 20);
            return tenant;
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(String subject) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(subject, null, List.of()));
    }
}
