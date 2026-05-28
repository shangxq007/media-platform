package com.example.platform.shared.audit;

import java.util.Map;

/**
 * Port interface for publishing admin audit events.
 *
 * <p>Implementations are responsible for:
 * <ul>
 *   <li>Outputting structured SLF4J logs</li>
 *   <li>Persisting to audit_records via {@link AuditPort}</li>
 *   <li>Sanitizing sensitive fields</li>
 *   <li>Ensuring persistence failures do not affect business logic</li>
 * </ul>
 *
 * <p>Defined in shared-kernel so all modules can depend on it.
 * Implementation lives in audit-compliance-module.
 */
public interface AdminAuditPublisher {

    /**
     * Publish an admin audit event.
     *
     * @param actor              the actor user ID
     * @param roles              the actor roles (comma-separated)
     * @param action             the action name (e.g. "ADMIN_LIST_TENANTS")
     * @param targetResourceType the target resource type
     * @param targetResourceId   the target resource ID (nullable)
     * @param targetTenantId     the target tenant ID (nullable)
     * @param result             "SUCCESS" or "DENIED" or "FAILED"
     */
    void publish(String actor, String roles, String action,
                 String targetResourceType, String targetResourceId,
                 String targetTenantId, String result);

    /**
     * Publish an admin audit event with additional details.
     */
    void publish(String actor, String roles, String action,
                 String targetResourceType, String targetResourceId,
                 String targetTenantId, String result,
                 Map<String, String> details);
}
