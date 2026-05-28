package com.example.platform.shared.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

/**
 * Structured audit logger for admin cross-tenant operations.
 *
 * <p>Outputs JSON-structured logs via SLF4J that integrate with the existing
 * logback-spring.xml JSON format. All fields are safe to log (no secrets/tokens).
 *
 * <p>This is a pure SLF4J logger with no persistence responsibility.
 * Persistence to {@code audit_records} is handled by the {@code AdminAuditPublisher}
 * implementation in the audit-compliance-module.
 *
 * <p>Usage:
 * <pre>
 * AdminAuditLogger.log("user-1", "ADMIN", "ADMIN_LIST_TENANTS", "tenant", null, "tenant-a", "SUCCESS");
 * </pre>
 */
public final class AdminAuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("ADMIN_AUDIT");

    private AdminAuditLogger() {}

    /**
     * Log an admin action.
     */
    public static void log(String actor, String roles, String action,
                           String targetResourceType, String targetResourceId,
                           String targetTenantId, String result) {
        log(actor, roles, action, targetResourceType, targetResourceId,
                targetTenantId, result, null);
    }

    /**
     * Log an admin action with additional details.
     */
    public static void log(String actor, String roles, String action,
                           String targetResourceType, String targetResourceId,
                           String targetTenantId, String result,
                           Map<String, String> details) {
        String requestId = safeMdc(TraceKeys.REQUEST_ID);
        String traceId = safeMdc(TraceKeys.TRACE_ID);

        auditLog.info(
                "event=admin_audit action={} actor={} roles={} targetResourceType={} " +
                "targetResourceId={} targetTenantId={} result={} requestId={} traceId={} details={}",
                action, actor, roles, targetResourceType, targetResourceId,
                targetTenantId, result, requestId, traceId,
                details != null ? details : Map.of());
    }

    private static String safeMdc(String key) {
        String value = MDC.get(key);
        return value != null ? value : "";
    }
}
