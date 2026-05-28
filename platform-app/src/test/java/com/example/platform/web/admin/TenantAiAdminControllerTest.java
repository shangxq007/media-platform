package com.example.platform.web.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.app.ai.TenantLitellmKeyService;
import com.example.platform.security.AdminAuditHelper;
import com.example.platform.shared.audit.AdminAuditPublisher;
import com.example.platform.web.admin.TenantAiAdminController.UpsertLitellmKeyRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

class TenantAiAdminControllerTest {

    private TenantLitellmKeyService keyService;
    private TenantAiAdminController controller;

    @BeforeEach
    void setUp() {
        keyService = mock(TenantLitellmKeyService.class);
        AdminAuditHelper auditHelper = new AdminAuditHelper(mock(AdminAuditPublisher.class));
        controller = new TenantAiAdminController(keyService, auditHelper);
    }

    @Test
    void getLitellmKey_oauth2AdminRole_succeeds() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addUserRole("ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(keyService.getView("tenant-a")).thenReturn(java.util.Optional.empty());

        var result = controller.getLitellmKey("tenant-a", request, response);

        assertNotNull(result);
        assertEquals(200, response.getStatus());
    }

    @Test
    void getLitellmKey_legacyJwtAdminRole_succeeds() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.roles", java.util.List.of("ADMIN"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(keyService.getView("tenant-a")).thenReturn(java.util.Optional.empty());

        var result = controller.getLitellmKey("tenant-a", request, response);

        assertNotNull(result);
        assertEquals(200, response.getStatus());
    }

    @Test
    void getLitellmKey_nonAdmin_returns403() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addUserRole("USER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(AccessDeniedException.class,
                () -> controller.getLitellmKey("tenant-a", request, response));
        assertEquals(403, response.getStatus());
        verifyNoInteractions(keyService);
    }

    @Test
    void getLitellmKey_noRole_returns403() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(AccessDeniedException.class,
                () -> controller.getLitellmKey("tenant-a", request, response));
        assertEquals(403, response.getStatus());
    }

    @Test
    void upsertLitellmKey_adminCanSetKey() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addUserRole("ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UpsertLitellmKeyRequest body = new UpsertLitellmKeyRequest("sk-test-key", "my-key", true);
        when(keyService.upsert("tenant-a", "sk-test-key", "my-key", true)).thenReturn(null);

        controller.upsertLitellmKey("tenant-a", body, request, response);

        verify(keyService).upsert("tenant-a", "sk-test-key", "my-key", true);
        assertEquals(200, response.getStatus());
    }

    @Test
    void upsertLitellmKey_nonAdmin_returns403() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addUserRole("EDITOR");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UpsertLitellmKeyRequest body = new UpsertLitellmKeyRequest("sk-test", null, true);

        assertThrows(AccessDeniedException.class,
                () -> controller.upsertLitellmKey("tenant-a", body, request, response));
        assertEquals(403, response.getStatus());
        verifyNoInteractions(keyService);
    }

    @Test
    void deleteLitellmKey_adminCanDelete() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addUserRole("ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.deleteLitellmKey("tenant-a", request, response);

        verify(keyService).remove("tenant-a");
        // @ResponseStatus(NO_CONTENT) is handled by Spring MVC, not in unit test
        // Verify the method completed without error (no exception thrown)
        assertEquals(200, response.getStatus());
    }

    @Test
    void deleteLitellmKey_nonAdmin_returns403() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addUserRole("USER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(AccessDeniedException.class,
                () -> controller.deleteLitellmKey("tenant-a", request, response));
        assertEquals(403, response.getStatus());
        verifyNoInteractions(keyService);
    }

    @Test
    void crossTenantAiKeyManagement_requiresAdmin() {
        // tenant-a user should NOT be able to manage tenant-b's AI keys
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addUserRole("USER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(AccessDeniedException.class,
                () -> controller.getLitellmKey("tenant-b", request, response));
        verifyNoInteractions(keyService);
    }
}
