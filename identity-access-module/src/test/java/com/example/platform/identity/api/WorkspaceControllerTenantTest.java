package com.example.platform.identity.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.entitlement.app.EntitlementPolicyService;
import com.example.platform.entitlement.app.EntitlementService;
import com.example.platform.entitlement.app.WorkspaceEntitlementPoolService;
import com.example.platform.entitlement.domain.EntitlementCommandType;
import com.example.platform.entitlement.domain.EntitlementCommandResult;
import com.example.platform.entitlement.domain.EntitlementDecision;
import com.example.platform.entitlement.domain.EntitlementGrantCommand;
import com.example.platform.entitlement.domain.WorkspaceMemberEntitlementGrant;
import com.example.platform.identity.api.WorkspaceController;
import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.app.WorkspaceService;
import com.example.platform.shared.audit.AdminAuditPublisher;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.time.Instant;

import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class WorkspaceControllerTenantTest {

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private WorkspaceEntitlementPoolService poolService;

    @Mock
    private EntitlementService entitlementService;

    @Mock
    private EntitlementPolicyService entitlementPolicyService;

    @Mock
    private AdminAuditPublisher auditPublisher;

    private WorkspaceController controller;

    @BeforeEach
    void setUp() {
        controller = new WorkspaceController(
                workspaceService, poolService, entitlementPolicyService, auditPublisher);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static EntitlementDecision sampleDecision() {
        return new EntitlementDecision(true, "ALLOW", null, null,
                "FREE", List.of(), null, null, null, null, null, List.of(), null, false);
    }

    @Test
    void previewEntitlementsUsesTenantContext() {
        TenantContext.set("tenant-a");
        when(entitlementPolicyService.validateExportDecision(
                "tenant-a", "user-1", "default_720p", "mp4", 60L))
                .thenReturn(sampleDecision());

        WorkspaceController.PreviewRequest request = new WorkspaceController.PreviewRequest("user-1", "default_720p", "mp4", 60L);
        EntitlementDecision result = controller.previewEntitlements("ws-1", request);

        assertNotNull(result);
        verify(entitlementPolicyService).validateExportDecision("tenant-a", "user-1", "default_720p", "mp4", 60L);
    }

    @Test
    void previewEntitlementsRejectsWithoutTenantContext() {
        TenantContext.clear();
        WorkspaceController.PreviewRequest request = new WorkspaceController.PreviewRequest("user-1", "default_720p", "mp4", 60L);

        assertThrows(IllegalArgumentException.class,
                () -> controller.previewEntitlements("ws-1", request));
    }

    @Test
    void previewEntitlementsUsesTenantContextNotWorkspaceId() {
        TenantContext.set("tenant-a");
        when(entitlementPolicyService.validateExportDecision(
                "tenant-a", "user-1", "default_720p", "mp4", 60L))
                .thenReturn(sampleDecision());

        WorkspaceController.PreviewRequest request = new WorkspaceController.PreviewRequest("user-1", "default_720p", "mp4", 60L);
        controller.previewEntitlements("tenant-b", request);

        verify(entitlementPolicyService).validateExportDecision("tenant-a", "user-1", "default_720p", "mp4", 60L);
    }

    @Test
    void fakeXTenantIdHeaderDoesNotChangeTenant() {
        TenantContext.set("tenant-a");
        when(entitlementPolicyService.validateExportDecision(
                "tenant-a", "user-1", "default_720p", "mp4", 60L))
                .thenReturn(sampleDecision());

        WorkspaceController.PreviewRequest request = new WorkspaceController.PreviewRequest("user-1", "default_720p", "mp4", 60L);
        controller.previewEntitlements("ws-1", request);

        verify(entitlementPolicyService).validateExportDecision("tenant-a", "user-1", "default_720p", "mp4", 60L);
    }

    @Test
    void createWorkspaceGrantUsesTenantContextAndCanonicalCommandMetadata() {
        TenantContext.set("tenant-a");
        Instant startsAt = Instant.parse("2026-08-29T01:00:00Z");
        Instant expiresAt = Instant.parse("2026-09-29T01:00:00Z");
        WorkspaceMemberEntitlementGrant expected = new WorkspaceMemberEntitlementGrant(
                "ws-grant-1", "ws-1", "member-1", "render", 25L,
                startsAt, expiresAt, "ACTIVE", "admin-1", startsAt, startsAt);
        WorkspaceController.CreateWorkspaceGrantRequest request =
                new WorkspaceController.CreateWorkspaceGrantRequest(
                        "member-1", "render", 25L, startsAt, expiresAt,
                        "admin-request-1", "create-grant-1", "project access", "trace-1");
        when(poolService.allocateToMember(
                "tenant-a", "ws-1", "render", "member-1", 25L,
                startsAt, expiresAt, "admin-1", "admin-request-1",
                "create-grant-1", "project access", "trace-1"))
                .thenReturn(expected);

        WorkspaceMemberEntitlementGrant result =
                controller.createWorkspaceGrant("ws-1", request, "admin-1");

        assertSame(expected, result);
        verify(poolService).allocateToMember(
                "tenant-a", "ws-1", "render", "member-1", 25L,
                startsAt, expiresAt, "admin-1", "admin-request-1",
                "create-grant-1", "project access", "trace-1");
    }

    @Test
    void revokeWorkspaceGrantUsesTenantContextMemberVersionAndCanonicalCommandMetadata() {
        TenantContext.set("tenant-a");
        EntitlementCommandResult expected = new EntitlementCommandResult("command-1", null);
        WorkspaceEntitlementPoolService canonicalPoolService =
                new WorkspaceEntitlementPoolService(null, entitlementService, null);
        WorkspaceController canonicalController = new WorkspaceController(
                workspaceService, canonicalPoolService, entitlementPolicyService, auditPublisher);
        WorkspaceController.RevokeGrantRequest request = new WorkspaceController.RevokeGrantRequest(
                "member-1", 7L, "admin-request-2", "revoke-grant-1",
                "member removed", "trace-2");
        when(entitlementService.execute(any(EntitlementGrantCommand.class))).thenReturn(expected);

        var result = canonicalController.revokeWorkspaceGrant(
                "ws-1", "ws-grant-1", request, "admin-1");

        assertEquals("revoked", result.get("status"));
        assertSame(expected, result.get("event"));
        var commandCaptor = org.mockito.ArgumentCaptor.forClass(EntitlementGrantCommand.class);
        verify(entitlementService).execute(commandCaptor.capture());
        EntitlementGrantCommand command = commandCaptor.getValue();
        assertAll(
                () -> assertEquals(EntitlementCommandType.WORKSPACE_REVOKE, command.commandType()),
                () -> assertEquals("tenant-a", command.principal().tenantId()),
                () -> assertEquals(PrincipalType.USER, command.principal().principalType()),
                () -> assertEquals("member-1", command.principal().principalId()),
                () -> assertEquals("ws-1", command.principal().workspaceId()),
                () -> assertEquals("ws-grant-1", command.grantId()),
                () -> assertEquals(7L, command.expectedVersion()),
                () -> assertEquals("ADMIN", command.sourceType()),
                () -> assertEquals("admin-request-2", command.sourceRef()),
                () -> assertEquals("revoke-grant-1", command.idempotencyKey()),
                () -> assertEquals("admin-1", command.actor()),
                () -> assertEquals("member removed", command.reason()),
                () -> assertEquals("trace-2", command.traceId()));
    }

    // ========== createWorkspace tenant resolution tests ==========

    @Test
    void createWorkspace_usesTenantContextWhenNoParam() {
        TenantContext.set("tenant-a");
        when(workspaceService.createWorkspace(eq("tenant-a"), any()))
                .thenReturn(new WorkspaceResponse("ws-1", "tenant-a", "My WS", null, null, "ACTIVE", null, null));

        CreateWorkspaceRequest body = new CreateWorkspaceRequest("My WS", null, null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        WorkspaceResponse result = controller.createWorkspace(null, body, request);

        assertNotNull(result);
        assertEquals("ws-1", result.id());
        verify(workspaceService).createWorkspace("tenant-a", body);
    }

    @Test
    void createWorkspace_usesTenantContextWhenParamMatches() {
        TenantContext.set("tenant-a");
        when(workspaceService.createWorkspace(eq("tenant-a"), any()))
                .thenReturn(new WorkspaceResponse("ws-1", "tenant-a", "My WS", null, null, "ACTIVE", null, null));

        CreateWorkspaceRequest body = new CreateWorkspaceRequest("My WS", null, null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        WorkspaceResponse result = controller.createWorkspace("tenant-a", body, request);

        assertNotNull(result);
        verify(workspaceService).createWorkspace("tenant-a", body);
    }

    @Test
    void createWorkspace_rejectsWithoutTenantContext() {
        TenantContext.clear();
        CreateWorkspaceRequest body = new CreateWorkspaceRequest("My WS", null, null);
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThrows(IllegalArgumentException.class,
                () -> controller.createWorkspace(null, body, request));
        verifyNoInteractions(workspaceService);
    }

    @Test
    void createWorkspace_crossTenantRequiresAdmin() {
        TenantContext.set("tenant-a");
        CreateWorkspaceRequest body = new CreateWorkspaceRequest("My WS", null, null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        // No admin role set

        assertThrows(SecurityException.class,
                () -> controller.createWorkspace("tenant-b", body, request));
        verifyNoInteractions(workspaceService);
    }

    @Test
    void createWorkspace_crossTenantAdminSucceeds() {
        TenantContext.set("tenant-a");
        when(workspaceService.createWorkspace(eq("tenant-b"), any()))
                .thenReturn(new WorkspaceResponse("ws-1", "tenant-b", "My WS", null, null, "ACTIVE", null, null));

        CreateWorkspaceRequest body = new CreateWorkspaceRequest("My WS", null, null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.roles", java.util.List.of("ADMIN"));

        WorkspaceResponse result = controller.createWorkspace("tenant-b", body, request);

        assertNotNull(result);
        assertEquals("tenant-b", result.tenantId());
        verify(workspaceService).createWorkspace("tenant-b", body);
    }
}
