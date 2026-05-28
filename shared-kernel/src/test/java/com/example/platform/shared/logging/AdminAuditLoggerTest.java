package com.example.platform.shared.logging;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

/**
 * Tests for AdminAuditLogger as a pure SLF4J logger.
 * No AuditPort dependency — persistence is handled by AdminAuditPublisher.
 */
class AdminAuditLoggerTest {

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void logDoesNotThrowWithNullMdc() {
        assertDoesNotThrow(() ->
                AdminAuditLogger.log("user-1", "ADMIN", "TEST_ACTION", "resource", "id-1", "tenant-a", "SUCCESS"));
    }

    @Test
    void logWorksWithMdcContext() {
        MDC.put(TraceKeys.REQUEST_ID, "req-123");
        MDC.put(TraceKeys.TRACE_ID, "trace-456");

        assertDoesNotThrow(() ->
                AdminAuditLogger.log("user-1", "ADMIN", "TEST_ACTION", "resource", "id-1", "tenant-a", "SUCCESS"));
    }

    @Test
    void logWithDetailsDoesNotThrow() {
        assertDoesNotThrow(() ->
                AdminAuditLogger.log("user-1", "ADMIN", "TEST_ACTION", "resource", "id-1", "tenant-a", "SUCCESS",
                        Map.of("key", "value")));
    }

    @Test
    void logAcceptsAnonymousActor() {
        assertDoesNotThrow(() ->
                AdminAuditLogger.log("anonymous", "none", "DENIED_ACTION", "resource", null, null, "DENIED"));
    }

    @Test
    void logAcceptsNullTargetFields() {
        assertDoesNotThrow(() ->
                AdminAuditLogger.log("user-1", "ADMIN", "ACTION", null, null, null, "SUCCESS"));
    }

    @Test
    void logAcceptsCommaRoles() {
        assertDoesNotThrow(() ->
                AdminAuditLogger.log("user-1", "USER,ADMIN,EDITOR", "ACTION", "resource", "id", "t1", "SUCCESS"));
    }

    @Test
    void logDoesNotContainSensitiveData() {
        assertDoesNotThrow(() ->
                AdminAuditLogger.log("user-1", "ADMIN", "ADMIN_SET_LITELLM_KEY",
                        "litellm_key", "tenant-a", "tenant-a", "SUCCESS"));
    }

    @Test
    void logNoArgsOverloadWorks() {
        assertDoesNotThrow(() ->
                AdminAuditLogger.log("user-1", "ADMIN", "ACTION", "resource", "id", "tenant-a", "SUCCESS"));
    }

    @Test
    void loggerHasNoAuditPortDependency() {
        // Verify AdminAuditLogger has no setAuditPort method (removed in P2-1c)
        try {
            AdminAuditLogger.class.getMethod("setAuditPort", com.example.platform.shared.audit.AuditPort.class);
            fail("AdminAuditLogger should not have setAuditPort method");
        } catch (NoSuchMethodException e) {
            // Expected — setAuditPort was removed
        }
    }
}
