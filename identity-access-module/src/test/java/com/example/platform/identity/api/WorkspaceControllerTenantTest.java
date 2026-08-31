package com.example.platform.identity.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.platform.entitlement.app.EntitlementDecisionService;
import com.example.platform.entitlement.app.WorkspaceEntitlementPoolService;
import com.example.platform.entitlement.domain.EntitlementDecision;
import com.example.platform.identity.api.dto.AddWorkspaceMemberRequest;
import com.example.platform.identity.api.dto.AssignRoleRequest;
import com.example.platform.identity.api.dto.CreateWorkspaceGroupRequest;
import com.example.platform.identity.api.dto.CreateWorkspaceRequest;
import com.example.platform.identity.app.WorkspaceService;
import com.example.platform.shared.audit.AdminAuditPublisher;
import com.example.platform.shared.authorization.AuthorizationDeniedException;
import com.example.platform.shared.web.TenantContext;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class WorkspaceControllerTenantTest {

    @Mock private WorkspaceService workspaceService;
    @Mock private WorkspaceEntitlementPoolService poolService;
    @Mock private EntitlementDecisionService entitlementDecisionService;
    @Mock private AdminAuditPublisher auditPublisher;

    private WorkspaceController controller;

    @BeforeEach
    void setUp() {
        controller = new WorkspaceController(
                workspaceService, poolService, entitlementDecisionService, auditPublisher);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void previewEntitlementsUsesTenantContext() {
        TenantContext.set("tenant-a");
        when(entitlementDecisionService.evaluate(any())).thenReturn(sampleDecision());

        var request = new WorkspaceController.PreviewRequest(
                "user-1", "default_720p", "mp4", 60L);
        EntitlementDecision result = controller.previewEntitlements("ws-1", request);

        assertNotNull(result);
        verify(entitlementDecisionService).evaluate(argThat(decisionRequest ->
                "tenant-a".equals(decisionRequest.tenantId())
                        && "user-1".equals(decisionRequest.subjectId())));
    }

    @Test
    void previewEntitlementsRejectsWithoutTenantContext() {
        var request = new WorkspaceController.PreviewRequest(
                "user-1", "default_720p", "mp4", 60L);

        assertThrows(IllegalArgumentException.class,
                () -> controller.previewEntitlements("ws-1", request));
    }

    @Test
    void managementMutationsDenyBeforeRequestAuthorityOrServicesAreUsed() {
        MockHttpServletRequest adminRequest = new MockHttpServletRequest();
        adminRequest.setAttribute("jwt.roles", List.of("ADMIN"));
        Instant now = Instant.parse("2026-08-31T00:00:00Z");

        assertUnavailable(() -> controller.createWorkspace(
                "request-tenant", new CreateWorkspaceRequest("name", null, null), adminRequest));
        assertUnavailable(() -> controller.addMember(
                "ws-1", new AddWorkspaceMemberRequest("member-1", "OWNER")));
        assertUnavailable(() -> controller.assignRole(
                "ws-1", "member-1", new AssignRoleRequest("OWNER", "request-actor")));
        assertUnavailable(() -> controller.revokeRole("ws-1", "member-1", "OWNER"));
        assertUnavailable(() -> controller.createGroup(
                "ws-1", new CreateWorkspaceGroupRequest("group", null)));
        assertUnavailable(() -> controller.createWorkspaceGrant(
                "ws-1",
                new WorkspaceController.CreateWorkspaceGrantRequest(
                        "member-1", "render", 10, now, now.plusSeconds(60),
                        "source", "idempotency", "reason", "trace"),
                "request-header-actor"));
        assertUnavailable(() -> controller.revokeWorkspaceGrant(
                "ws-1", "grant-1",
                new WorkspaceController.RevokeGrantRequest(
                        "member-1", 1, "source", "idempotency", "reason", "trace"),
                "request-header-actor"));

        verifyNoInteractions(workspaceService, poolService, auditPublisher);
    }

    private static void assertUnavailable(org.junit.jupiter.api.function.Executable invocation) {
        AuthorizationDeniedException failure = assertThrows(
                AuthorizationDeniedException.class, invocation);
        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
    }

    private static EntitlementDecision sampleDecision() {
        return new EntitlementDecision(true, "ALLOW", null, null,
                "FREE", List.of(), null, null, null, null, null, List.of(), null, false);
    }
}
