package com.example.platform.extension.app;

import com.example.platform.extension.domain.ExtensionAuditEvent;
import com.example.platform.shared.audit.AuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExtensionAuditServiceTest {

    private AuditPort auditPort;
    private ExtensionAuditService auditService;

    @BeforeEach
    void setUp() {
        auditPort = mock(AuditPort.class);
        auditService = new ExtensionAuditService(auditPort);
    }

    @Test
    void shouldRecordRegistration() {
        ExtensionAuditEvent event = auditService.recordRegistration(
                "ext-1", "1.0.0", "SEMI_TRUSTED", "admin",
                Map.of("type", "RENDER"));

        assertNotNull(event);
        assertEquals("ext-1", event.extensionCode());
        assertEquals(ExtensionAuditEvent.EventType.EXTENSION_REGISTERED.name(), event.eventType());
        assertEquals("admin", event.actor());
        assertEquals("SEMI_TRUSTED", event.trustLevel());
        verify(auditPort).record(eq("admin"), eq("EXTENSION_REGISTERED"), eq("EXTENSION"),
                eq("extension"), eq("ext-1"), any(Map.class));
    }

    @Test
    void shouldRecordUnload() {
        ExtensionAuditEvent event = auditService.recordUnload("ext-1", "1.0.0", "admin");

        assertEquals(ExtensionAuditEvent.EventType.EXTENSION_UNLOADED.name(), event.eventType());
        assertEquals("INFO", event.severity());
    }

    @Test
    void shouldRecordExecutionStart() {
        ExtensionAuditEvent event = auditService.recordExecutionStart(
                "ext-1", "1.0.0", "tenant-1", "user-1", "trace-1", "SEMI_TRUSTED");

        assertEquals(ExtensionAuditEvent.EventType.EXTENSION_EXECUTION_STARTED.name(), event.eventType());
        assertEquals("tenant-1", event.tenantId());
        assertEquals("user-1", event.userId());
        assertEquals("trace-1", event.traceId());
    }

    @Test
    void shouldRecordExecutionComplete() {
        ExtensionAuditEvent event = auditService.recordExecutionComplete(
                "ext-1", "1.0.0", "tenant-1", "user-1", "trace-1", "SEMI_TRUSTED", 150L, 2048);

        assertEquals(ExtensionAuditEvent.EventType.EXTENSION_EXECUTION_COMPLETED.name(), event.eventType());
        assertEquals("INFO", event.severity());
    }

    @Test
    void shouldRecordExecutionTimeout() {
        ExtensionAuditEvent event = auditService.recordExecutionTimeout(
                "ext-1", "1.0.0", "tenant-1", "user-1", "trace-1", 30000L);

        assertEquals(ExtensionAuditEvent.EventType.EXTENSION_EXECUTION_TIMEOUT.name(), event.eventType());
        assertEquals("WARN", event.severity());
    }

    @Test
    void shouldRecordExecutionFailed() {
        ExtensionAuditEvent event = auditService.recordExecutionFailed(
                "ext-1", "1.0.0", "tenant-1", "user-1", "trace-1", "ERR-001", "Something broke");

        assertEquals(ExtensionAuditEvent.EventType.EXTENSION_EXECUTION_FAILED.name(), event.eventType());
        assertEquals("ERROR", event.severity());
    }

    @Test
    void shouldRecordRollback() {
        ExtensionAuditEvent event = auditService.recordRollback(
                "ext-1", "2.0.0", "1.0.0", "admin");

        assertEquals(ExtensionAuditEvent.EventType.EXTENSION_ROLLED_BACK.name(), event.eventType());
        assertEquals("WARN", event.severity());
    }

    @Test
    void shouldRecordSecurityViolation() {
        ExtensionAuditEvent event = auditService.recordSecurityViolation(
                "ext-1", "user-1", "Attempted file access");

        assertEquals(ExtensionAuditEvent.EventType.SECURITY_VIOLATION.name(), event.eventType());
        assertEquals("CRITICAL", event.severity());
    }

    @Test
    void shouldGetRecentEvents() {
        auditService.recordRegistration("ext-1", "1.0.0", "SEMI_TRUSTED", "admin", Map.of());
        auditService.recordRegistration("ext-2", "1.0.0", "FULLY_TRUSTED", "admin", Map.of());
        auditService.recordUnload("ext-1", "1.0.0", "admin");

        List<ExtensionAuditEvent> recent = auditService.getRecentEvents(2);
        assertEquals(2, recent.size());
    }

    @Test
    void shouldGetEventsByExtension() {
        auditService.recordRegistration("ext-1", "1.0.0", "SEMI_TRUSTED", "admin", Map.of());
        auditService.recordRegistration("ext-2", "1.0.0", "FULLY_TRUSTED", "admin", Map.of());
        auditService.recordUnload("ext-1", "1.0.0", "admin");

        List<ExtensionAuditEvent> ext1Events = auditService.getEventsByExtension("ext-1");
        assertEquals(2, ext1Events.size());

        List<ExtensionAuditEvent> ext2Events = auditService.getEventsByExtension("ext-2");
        assertEquals(1, ext2Events.size());
    }
}
