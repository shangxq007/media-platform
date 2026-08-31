package com.example.platform.web.collaboration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.platform.shared.authorization.AuthorizationDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CollaborationControllerContainmentTest {

    @Test
    void requestTenantGrantorAndIdOnlyRevocationDenyBeforeServiceUse() {
        SharedResourceService service = mock(SharedResourceService.class);
        CollaborationController controller = new CollaborationController(service);
        var body = new CollaborationController.GrantSharedResourceRequest(
                "request-tenant", "project", "resource", "name",
                "request-grantor", "recipient", "ADMIN");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-ID", "request-header-grantor");

        assertUnavailable(() -> controller.grantAccess(body, request));
        assertUnavailable(() -> controller.revokeMyGrant("request-grant-id"));

        verifyNoInteractions(service);
    }

    private static void assertUnavailable(org.junit.jupiter.api.function.Executable invocation) {
        AuthorizationDeniedException failure = assertThrows(
                AuthorizationDeniedException.class, invocation);
        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
    }
}
