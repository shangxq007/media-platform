package com.example.platform.identity.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.platform.identity.api.dto.CreateApiKeyRequest;
import com.example.platform.identity.api.dto.CreateProjectRequest;
import com.example.platform.identity.api.dto.CreateTenantRequest;
import com.example.platform.identity.api.dto.CreateUserRequest;
import com.example.platform.identity.app.TenantProjectService;
import com.example.platform.shared.audit.AdminAuditPublisher;
import com.example.platform.shared.authorization.AuthorizationDeniedException;
import org.junit.jupiter.api.Test;

class TenantProjectControllerContainmentTest {

    @Test
    void managementMutationsDenyBeforeIdentifiersReachTheService() {
        TenantProjectService service = mock(TenantProjectService.class);
        AdminAuditPublisher audit = mock(AdminAuditPublisher.class);
        TenantProjectController controller = new TenantProjectController(service, audit);

        assertUnavailable(() -> controller.createTenant(new CreateTenantRequest("tenant")));
        assertUnavailable(() -> controller.createProject(
                "request-tenant", new CreateProjectRequest("project", null)));
        assertUnavailable(() -> controller.createUser(
                "request-tenant", new CreateUserRequest("user", "user@example.test", "ADMIN")));
        assertUnavailable(() -> controller.createApiKey(
                "request-tenant", new CreateApiKeyRequest("request-principal")));

        verifyNoInteractions(service, audit);
    }

    private static void assertUnavailable(org.junit.jupiter.api.function.Executable invocation) {
        AuthorizationDeniedException failure = assertThrows(
                AuthorizationDeniedException.class, invocation);
        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
    }
}
