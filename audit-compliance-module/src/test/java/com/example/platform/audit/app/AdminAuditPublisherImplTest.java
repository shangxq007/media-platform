package com.example.platform.audit.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.logging.TraceKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
class AdminAuditPublisherImplTest {

    @Mock
    private AuditPort auditPort;

    private AdminAuditPublisherImpl publisher;

    @BeforeEach
    void setUp() {
        MDC.clear();
        publisher = new AdminAuditPublisherImpl(auditPort);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void publish_callsAuditPort() {
        publisher.publish("user-1", "ADMIN", "ADMIN_LIST_TENANTS",
                "tenant", null, "tenant-a", "SUCCESS");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditPort).record(
                eq("ADMIN"),
                eq("ADMIN_LIST_TENANTS"),
                eq("ADMIN_AUDIT"),
                eq("tenant"),
                isNull(),
                payloadCaptor.capture());

        assertEquals("ADMIN", payloadCaptor.getValue().get("roles"));
        assertEquals("tenant-a", payloadCaptor.getValue().get("targetTenantId"));
        assertEquals("SUCCESS", payloadCaptor.getValue().get("result"));
    }

    @Test
    void publish_deniedResult_persistsViaAuditPort() {
        publisher.publish("user-1", "USER", "ADMIN_ACTION",
                "resource", "id-1", "tenant-a", "DENIED");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditPort).record(
                eq("ADMIN"),
                eq("ADMIN_ACTION"),
                eq("ADMIN_AUDIT"),
                eq("resource"),
                eq("id-1"),
                payloadCaptor.capture());

        assertEquals("DENIED", payloadCaptor.getValue().get("result"));
    }

    @Test
    void publish_withDetails_sanitizesSensitiveFields() {
        Map<String, String> details = Map.of(
                "reason", "expired",
                "apiKey", "sk-secret-123",
                "Authorization", "Bearer token-xyz");

        publisher.publish("user-1", "ADMIN", "ADMIN_ACTION",
                "resource", "id", "tenant-a", "SUCCESS", details);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditPort).record(any(), any(), any(), any(), any(), payloadCaptor.capture());

        @SuppressWarnings("unchecked")
        Map<String, String> savedDetails = (Map<String, String>) payloadCaptor.getValue().get("details");
        assertEquals("expired", savedDetails.get("reason"));
        assertEquals("[REDACTED]", savedDetails.get("apiKey"));
        assertEquals("[REDACTED]", savedDetails.get("Authorization"));
    }

    @Test
    void publish_auditPortFailure_doesNotThrow() {
        doThrow(new RuntimeException("DB down")).when(auditPort)
                .record(any(), any(), any(), any(), any(), any());

        assertDoesNotThrow(() ->
                publisher.publish("user-1", "ADMIN", "ACTION",
                        "resource", "id", "tenant-a", "SUCCESS"));
    }

    @Test
    void publish_payloadContainsRequestIdAndTraceId() {
        MDC.put(TraceKeys.REQUEST_ID, "req-123");
        MDC.put(TraceKeys.TRACE_ID, "trace-456");

        publisher.publish("user-1", "ADMIN", "ACTION",
                "resource", "id", "tenant-a", "SUCCESS");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditPort).record(any(), any(), any(), any(), any(), payloadCaptor.capture());

        assertEquals("req-123", payloadCaptor.getValue().get("requestId"));
        assertEquals("trace-456", payloadCaptor.getValue().get("traceId"));
    }

    @Test
    void publish_nullResourceType_usesUnknown() {
        publisher.publish("user-1", "ADMIN", "ACTION",
                null, null, null, "SUCCESS");

        verify(auditPort).record(
                eq("ADMIN"), eq("ACTION"), eq("ADMIN_AUDIT"),
                eq("unknown"), isNull(), any());
    }

    @Test
    void publish_litellmKeyNotInPayload() {
        Map<String, String> details = Map.of(
                "virtualKey", "sk-litellm-secret-key-12345",
                "keyAlias", "production-key");

        publisher.publish("admin-1", "ADMIN", "ADMIN_SET_LITELLM_KEY",
                "litellm_key", "tenant-a", "tenant-a", "SUCCESS", details);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditPort).record(any(), any(), any(), any(), any(), payloadCaptor.capture());

        @SuppressWarnings("unchecked")
        Map<String, String> savedDetails = (Map<String, String>) payloadCaptor.getValue().get("details");
        assertEquals("[REDACTED]", savedDetails.get("virtualKey"),
                "LiteLLM virtual key must be redacted");
        assertEquals("production-key", savedDetails.get("keyAlias"),
                "Non-sensitive fields should be preserved");
    }
}
