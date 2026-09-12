package com.example.platform.security;

import com.example.platform.identity.api.authorization.AuthorizationDeniedException;
import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class IdentityAuthorizationDenialTransportTest {
    @Test void typedDenialKeepsForbiddenWireContractWithoutTransportBaseClass() {
        var decision = AuthorizationDecision.deny("RBAC_DENY", "RBAC", "no perm");
        var denial = new AuthorizationDeniedException(decision);
        assertEquals(RuntimeException.class, denial.getClass().getSuperclass());
        var request = new MockHttpServletRequest("POST", "/api/protected");
        var response = new GlobalExceptionHandler(Optional.empty()).handleAuthorizationDenied(denial, request);
        assertEquals(403, response.getStatus());
        assertEquals("SECURITY-403-001", response.getProperties().get("errorCode"));
        assertEquals("no perm", response.getDetail());
        assertSame(decision, denial.decision());
        assertThrows(IllegalArgumentException.class,
                () -> new AuthorizationDeniedException(AuthorizationDecision.allow("RBAC")));
    }
}
