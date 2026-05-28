package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.platform.shared.audit.AdminAuditPublisher;
import com.example.platform.shared.logging.TraceKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class AdminAuditHelperTest {

    @Mock
    private AdminAuditPublisher publisher;

    private AdminAuditHelper helper;

    @BeforeEach
    void setUp() {
        MDC.clear();
        helper = new AdminAuditHelper(publisher);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void log_callsPublisher() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.subject", "user-1");
        request.setAttribute("jwt.roles", List.of("ADMIN"));

        helper.log(request, "ADMIN_LIST_TENANTS", "tenant", null, "tenant-a", "SUCCESS");

        verify(publisher).publish(
                eq("user-1"),
                eq("ADMIN"),
                eq("ADMIN_LIST_TENANTS"),
                eq("tenant"),
                isNull(),
                eq("tenant-a"),
                eq("SUCCESS"),
                isNull());
    }

    @Test
    void logDenied_callsPublisherWithDeniedResult() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.subject", "user-1");
        request.setAttribute("jwt.roles", List.of("USER"));

        helper.logDenied(request, "ADMIN_LIST_TENANTS", "tenant", null, "tenant-a");

        verify(publisher).publish(
                eq("user-1"),
                eq("USER"),
                eq("ADMIN_LIST_TENANTS"),
                eq("tenant"),
                isNull(),
                eq("tenant-a"),
                eq("DENIED"),
                isNull());
    }

    @Test
    void log_withDetails_sanitizesSensitiveFields() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.subject", "user-1");
        request.setAttribute("jwt.roles", List.of("ADMIN"));

        Map<String, String> details = Map.of(
                "reason", "expired",
                "apiKey", "sk-secret-123",
                "Authorization", "Bearer token-xyz");

        helper.log(request, "ADMIN_ACTION", "resource", "id", "tenant-a", "SUCCESS", details);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(
                eq("user-1"), eq("ADMIN"), eq("ADMIN_ACTION"),
                eq("resource"), eq("id"), eq("tenant-a"), eq("SUCCESS"),
                detailsCaptor.capture());

        Map<String, String> savedDetails = detailsCaptor.getValue();
        assertEquals("expired", savedDetails.get("reason"));
        assertEquals("[REDACTED]", savedDetails.get("apiKey"));
        assertEquals("[REDACTED]", savedDetails.get("Authorization"));
    }

    @Test
    void log_publisherFailure_doesNotThrow() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.subject", "user-1");
        request.setAttribute("jwt.roles", List.of("ADMIN"));

        doThrow(new RuntimeException("DB down")).when(publisher)
                .publish(any(), any(), any(), any(), any(), any(), any(), any());

        assertDoesNotThrow(() ->
                helper.log(request, "ADMIN_ACTION", "resource", "id", "tenant-a", "SUCCESS"));
    }

    @Test
    void noStaticPublisherField() {
        try {
            AdminAuditHelper.class.getDeclaredField("staticPublisher");
            fail("AdminAuditHelper should not have staticPublisher field");
        } catch (NoSuchFieldException e) {
            // Expected
        }
    }

    @Test
    void noPostConstructAnnotation() {
        try {
            AdminAuditHelper.class.getDeclaredMethod("init");
            fail("AdminAuditHelper should not have init() method");
        } catch (NoSuchMethodException e) {
            // Expected
        }
    }

    @Test
    void sanitizeDetails_redactsSensitiveKeys() {
        Map<String, String> details = Map.of(
                "reason", "test",
                "apiKey", "sk-123",
                "api_key", "sk-456",
                "token", "tok-789",
                "secret", "s-abc",
                "password", "pw-xyz",
                "Authorization", "Bearer abc",
                "signedUrl", "https://s3.amazonaws.com/signed?X-Amz-Signature=abc",
                "virtualKey", "vk-123");

        Map<String, String> sanitized = AdminAuditHelper.sanitizeDetails(details);

        assertEquals("test", sanitized.get("reason"));
        assertEquals("[REDACTED]", sanitized.get("apiKey"));
        assertEquals("[REDACTED]", sanitized.get("api_key"));
        assertEquals("[REDACTED]", sanitized.get("token"));
        assertEquals("[REDACTED]", sanitized.get("secret"));
        assertEquals("[REDACTED]", sanitized.get("password"));
        assertEquals("[REDACTED]", sanitized.get("Authorization"));
        assertEquals("[REDACTED]", sanitized.get("signedUrl"));
        assertEquals("[REDACTED]", sanitized.get("virtualKey"));
    }

    @Test
    void sanitizeDetails_preservesNonSensitiveKeys() {
        Map<String, String> details = Map.of(
                "reason", "expired",
                "tenantId", "tenant-a",
                "action", "RETRY",
                "status", "FAILED");

        Map<String, String> sanitized = AdminAuditHelper.sanitizeDetails(details);

        assertEquals("expired", sanitized.get("reason"));
        assertEquals("tenant-a", sanitized.get("tenantId"));
        assertEquals("RETRY", sanitized.get("action"));
        assertEquals("FAILED", sanitized.get("status"));
    }

    @Test
    void sanitizeDetails_handlesEmptyMap() {
        Map<String, String> sanitized = AdminAuditHelper.sanitizeDetails(Map.of());
        assertTrue(sanitized.isEmpty());
    }

    @Test
    void extractActor_fromJwtSubject() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.subject", "user-42");

        assertEquals("user-42", AdminAuditHelper.extractActor(request));
    }

    @Test
    void extractActor_anonymousWhenNoSubject() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertEquals("anonymous", AdminAuditHelper.extractActor(request));
    }

    @Test
    void extractRoles_fromJwtRolesList() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.roles", List.of("ADMIN", "USER"));

        assertEquals("ADMIN,USER", AdminAuditHelper.extractRoles(request));
    }

    @Test
    void extractRoles_fromJwtRolesString() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jwt.roles", "USER,EDITOR");

        assertEquals("USER,EDITOR", AdminAuditHelper.extractRoles(request));
    }

    @Test
    void extractRoles_noneWhenNoRoles() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertEquals("none", AdminAuditHelper.extractRoles(request));
    }
}
