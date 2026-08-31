package com.example.platform.identity.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.platform.identity.app.TenantProjectService;
import com.example.platform.shared.audit.AdminAuditPublisher;
import com.example.platform.shared.authorization.AuthorizationDeniedException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TenantProjectControllerAdminTest {

    @Test
    void requestAndContainerAdminRolesCannotEstablishGlobalTenantAuthority() {
        TenantProjectService service = mock(TenantProjectService.class);
        AdminAuditPublisher audit = mock(AdminAuditPublisher.class);
        TenantProjectController controller = new TenantProjectController(service, audit);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addUserRole("ADMIN");
        request.setAttribute("jwt.roles", List.of("ADMIN"));

        AuthorizationDeniedException failure = assertThrows(
                AuthorizationDeniedException.class,
                () -> controller.listAllTenants(request, new MockHttpServletResponse(), 100));

        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
        verifyNoInteractions(service, audit);
    }
}
