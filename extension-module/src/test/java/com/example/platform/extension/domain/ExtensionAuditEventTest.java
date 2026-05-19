package com.example.platform.extension.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionAuditEventTest {

    @Test
    void shouldCreateValidEvent() {
        ExtensionAuditEvent event = new ExtensionAuditEvent(
                "aud-1", "ext-1", "1.0.0",
                ExtensionAuditEvent.EventType.EXTENSION_REGISTERED.name(),
                "admin", "tenant-1", "user-1", "trace-1",
                "SEMI_TRUSTED", null, "INFO", OffsetDateTime.now());

        assertEquals("aud-1", event.id());
        assertEquals("ext-1", event.extensionCode());
        assertEquals("admin", event.actor());
        assertEquals("INFO", event.severity());
    }

    @Test
    void shouldDefaultSeverityToInfo() {
        ExtensionAuditEvent event = new ExtensionAuditEvent(
                "aud-1", "ext-1", "1.0.0",
                ExtensionAuditEvent.EventType.EXTENSION_REGISTERED.name(),
                "admin", null, null, null, null, null, null, OffsetDateTime.now());

        assertEquals("INFO", event.severity());
    }

    @Test
    void shouldThrowOnBlankExtensionCode() {
        assertThrows(IllegalArgumentException.class, () ->
                new ExtensionAuditEvent("aud-1", "", "1.0.0",
                        ExtensionAuditEvent.EventType.EXTENSION_REGISTERED.name(),
                        "admin", null, null, null, null, null, "INFO", OffsetDateTime.now()));
    }

    @Test
    void shouldThrowOnBlankEventType() {
        assertThrows(IllegalArgumentException.class, () ->
                new ExtensionAuditEvent("aud-1", "ext-1", "1.0.0",
                        "", "admin", null, null, null, null, null, "INFO", OffsetDateTime.now()));
    }

    @Test
    void shouldThrowOnBlankActor() {
        assertThrows(IllegalArgumentException.class, () ->
                new ExtensionAuditEvent("aud-1", "ext-1", "1.0.0",
                        ExtensionAuditEvent.EventType.EXTENSION_REGISTERED.name(),
                        "", null, null, null, null, null, "INFO", OffsetDateTime.now()));
    }

    @Test
    void shouldHaveAllEventTypes() {
        assertNotNull(ExtensionAuditEvent.EventType.EXTENSION_REGISTERED);
        assertNotNull(ExtensionAuditEvent.EventType.EXTENSION_UNLOADED);
        assertNotNull(ExtensionAuditEvent.EventType.EXTENSION_ROLLED_BACK);
        assertNotNull(ExtensionAuditEvent.EventType.EXTENSION_EXECUTION_STARTED);
        assertNotNull(ExtensionAuditEvent.EventType.EXTENSION_EXECUTION_COMPLETED);
        assertNotNull(ExtensionAuditEvent.EventType.EXTENSION_EXECUTION_TIMEOUT);
        assertNotNull(ExtensionAuditEvent.EventType.EXTENSION_EXECUTION_FAILED);
        assertNotNull(ExtensionAuditEvent.EventType.ROUTING_RULE_CREATED);
        assertNotNull(ExtensionAuditEvent.EventType.RESOURCE_LIMIT_EXCEEDED);
        assertNotNull(ExtensionAuditEvent.EventType.SECURITY_VIOLATION);
        assertNotNull(ExtensionAuditEvent.EventType.REVIEW_REQUIRED);
    }
}
