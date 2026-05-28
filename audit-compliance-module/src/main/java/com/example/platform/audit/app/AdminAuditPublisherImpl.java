package com.example.platform.audit.app;

import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.audit.AdminAuditPublisher;
import com.example.platform.shared.logging.AdminAuditLogger;
import com.example.platform.shared.logging.TraceKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation of {@link AdminAuditPublisher}.
 *
 * <p>Publishes admin audit events by:
 * <ol>
 *   <li>Outputting structured SLF4J logs via {@link AdminAuditLogger}</li>
 *   <li>Persisting to {@code audit_records} via {@link AuditPort}</li>
 *   <li>Sanitizing sensitive fields in details</li>
 * </ol>
 *
 * <p>Persistence failures are caught and logged as warnings — they never
 * affect the caller's business logic.
 */
@Component
public class AdminAuditPublisherImpl implements AdminAuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditPublisherImpl.class);

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "authorization", "cookie", "token", "accesstoken", "refreshtoken",
            "apikey", "api_key", "key", "secret", "password", "passwd",
            "signedurl", "signed_url", "virtualkey", "virtual_key",
            "litellmkey", "litellm_key", "bearer"
    );

    private final AuditPort auditPort;

    public AdminAuditPublisherImpl(AuditPort auditPort) {
        this.auditPort = auditPort;
    }

    @Override
    public void publish(String actor, String roles, String action,
                        String targetResourceType, String targetResourceId,
                        String targetTenantId, String result) {
        publish(actor, roles, action, targetResourceType, targetResourceId,
                targetTenantId, result, null);
    }

    @Override
    public void publish(String actor, String roles, String action,
                        String targetResourceType, String targetResourceId,
                        String targetTenantId, String result,
                        Map<String, String> details) {
        Map<String, String> safeDetails = details != null ? sanitizeDetails(details) : null;

        // 1. Always output SLF4J structured log
        AdminAuditLogger.log(actor, roles, action, targetResourceType, targetResourceId,
                targetTenantId, result, safeDetails != null ? safeDetails : Map.of());

        // 2. Persist to audit_records
        persist(actor, roles, action, targetResourceType, targetResourceId,
                targetTenantId, result, safeDetails);
    }

    private void persist(String actor, String roles, String action,
                         String targetResourceType, String targetResourceId,
                         String targetTenantId, String result,
                         Map<String, String> details) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("roles", roles);
            payload.put("targetTenantId", targetTenantId);
            payload.put("result", result);
            payload.put("requestId", safeMdc(TraceKeys.REQUEST_ID));
            payload.put("traceId", safeMdc(TraceKeys.TRACE_ID));
            if (details != null && !details.isEmpty()) {
                payload.put("details", details);
            }

            auditPort.record("ADMIN", action, AuditCategory.ADMIN_AUDIT.name(),
                    targetResourceType != null ? targetResourceType : "unknown",
                    targetResourceId, payload);
        } catch (Exception e) {
            log.warn("Failed to persist admin audit: action={} error={}", action, e.getMessage());
        }
    }

    private Map<String, String> sanitizeDetails(Map<String, String> details) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : details.entrySet()) {
            String key = entry.getKey().toLowerCase().replace("-", "").replace("_", "");
            if (SENSITIVE_KEYS.contains(key)) {
                sanitized.put(entry.getKey(), "[REDACTED]");
            } else {
                sanitized.put(entry.getKey(), entry.getValue());
            }
        }
        return sanitized;
    }

    private static String safeMdc(String key) {
        String value = MDC.get(key);
        return value != null ? value : "";
    }
}
