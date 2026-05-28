package com.example.platform.web.collaboration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.entitlement.app.EntitlementPolicyService;
import com.example.platform.entitlement.app.EntitlementService;
import com.example.platform.entitlement.app.WorkspaceEntitlementPoolService;
import com.example.platform.entitlement.domain.EntitlementDecision;
import com.example.platform.identity.api.WorkspaceController;
import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.app.WorkspaceService;
import com.example.platform.security.AdminAuditHelper;
import com.example.platform.shared.audit.AdminAuditPublisher;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class SharedResourceAdminControllerTenantTest {

    @Mock
    private com.example.platform.web.collaboration.SharedResourceService sharedResourceService;

    private SharedResourceAdminController controller;

    @BeforeEach
    void setUp() {
        AdminAuditHelper auditHelper = new AdminAuditHelper(mock(AdminAuditPublisher.class));
        controller = new SharedResourceAdminController(sharedResourceService, auditHelper);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listGrantsUsesTenantContext() {
        TenantContext.set("tenant-a");
        when(sharedResourceService.listGrantsForTenant("tenant-a", false))
                .thenReturn(List.of());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addUserRole("ADMIN");

        controller.listGrants(null, false, request);

        verify(sharedResourceService).listGrantsForTenant("tenant-a", false);
        verify(sharedResourceService, never()).listGrantsForTenant("tenant-b", false);
    }

    @Test
    void listGrantsRejectsMismatchedTenantId() {
        TenantContext.set("tenant-a");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addUserRole("ADMIN");

        assertThrows(SecurityException.class,
                () -> controller.listGrants("tenant-b", false, request));
    }

    @Test
    void listGrantsRejectsWithoutTenantContext() {
        TenantContext.clear();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addUserRole("ADMIN");

        assertThrows(IllegalArgumentException.class,
                () -> controller.listGrants(null, false, request));
    }

    @Test
    void listGrantsRejectsNonAdmin() {
        TenantContext.set("tenant-a");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addUserRole("USER");

        assertThrows(SecurityException.class,
                () -> controller.listGrants(null, false, request));
    }

    @Test
    void revokeGrantRejectsNonAdmin() {
        TenantContext.set("tenant-a");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addUserRole("USER");

        assertThrows(SecurityException.class,
                () -> controller.revokeGrant("grant-1", request));
    }

    @Test
    void listGrantsAcceptsAdminViaJwtRolesAttribute() {
        TenantContext.set("tenant-a");
        when(sharedResourceService.listGrantsForTenant("tenant-a", false))
                .thenReturn(List.of());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.roles", List.of("ADMIN"));

        controller.listGrants(null, false, request);

        verify(sharedResourceService).listGrantsForTenant("tenant-a", false);
    }

    @Test
    void listGrantsAcceptsAdminViaCommaSeparatedJwtRoles() {
        TenantContext.set("tenant-a");
        when(sharedResourceService.listGrantsForTenant("tenant-a", false))
                .thenReturn(List.of());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.roles", "USER,ADMIN");

        controller.listGrants(null, false, request);

        verify(sharedResourceService).listGrantsForTenant("tenant-a", false);
    }
}
