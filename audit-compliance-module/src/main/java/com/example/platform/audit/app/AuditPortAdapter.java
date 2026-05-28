package com.example.platform.audit.app;

import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.logging.TraceKeys;
import com.example.platform.shared.web.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Adapter that exposes AuditService as AuditPort.
 *
 * <p>Maps the 6-parameter AuditPort interface to the 7-parameter AuditService method,
 * deriving actorId from request context (MDC principal or TenantContext).
 */
@Component
public class AuditPortAdapter implements AuditPort {

    private static final Logger log = LoggerFactory.getLogger(AuditPortAdapter.class);

    private final AuditService auditService;

    public AuditPortAdapter(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void record(String actorType, String action, String category,
            String resourceType, String resourceId, Map<String, Object> payload) {
        String actorId = resolveActorId();
        AuditCategory auditCategory = parseCategory(category);
        auditService.record(actorType, actorId, action, resourceType, resourceId, payload, auditCategory);
    }

    /**
     * Resolves actorId from request context.
     * Priority: MDC principal > TenantContext > "system"
     */
    private static String resolveActorId() {
        String principal = org.slf4j.MDC.get(TraceKeys.PRINCIPAL);
        if (principal != null && !principal.isBlank()) {
            return principal;
        }
        String tenantId = TenantContext.get();
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId;
        }
        return "system";
    }

    /**
     * Parses category string to AuditCategory enum.
     * Returns UNKNOWN for null/blank/unrecognized values — never returns null.
     */
    private static AuditCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            return AuditCategory.UNKNOWN;
        }
        try {
            return AuditCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.debug("Unrecognized audit category: {}, falling back to UNKNOWN", category);
            return AuditCategory.UNKNOWN;
        }
    }
}
