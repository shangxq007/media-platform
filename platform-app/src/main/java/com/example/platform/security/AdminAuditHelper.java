package com.example.platform.security;

import com.example.platform.shared.audit.AdminAuditPublisher;
import com.example.platform.shared.logging.AdminAuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper for logging admin cross-tenant operations.
 *
 * <p>Extracts actor/roles from request and delegates to {@link AdminAuditPublisher}
 * which handles both SLF4J output and audit_records persistence.
 *
 * <p>Standard Spring bean — inject via constructor, do NOT use static methods.
 */
@Component
public class AdminAuditHelper {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditHelper.class);

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "authorization", "cookie", "token", "accesstoken", "refreshtoken",
            "apikey", "api_key", "key", "secret", "password", "passwd",
            "signedurl", "signed_url", "virtualkey", "virtual_key",
            "litellmkey", "litellm_key", "bearer"
    );

    private final AdminAuditPublisher publisher;

    public AdminAuditHelper(AdminAuditPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Log an admin action, extracting actor and roles from the request.
     */
    public void log(HttpServletRequest request, String action,
                    String targetResourceType, String targetResourceId,
                    String targetTenantId, String result) {
        log(request, action, targetResourceType, targetResourceId, targetTenantId, result, null);
    }

    /**
     * Log an admin action with additional details.
     */
    public void log(HttpServletRequest request, String action,
                    String targetResourceType, String targetResourceId,
                    String targetTenantId, String result,
                    Map<String, String> details) {
        String actor = extractActor(request);
        String roles = extractRoles(request);
        Map<String, String> safeDetails = details != null ? sanitizeDetails(details) : null;

        try {
            publisher.publish(actor, roles, action, targetResourceType, targetResourceId,
                    targetTenantId, result, safeDetails);
        } catch (Exception e) {
            log.warn("AdminAuditPublisher failed: action={} error={}", action, e.getMessage());
        }
    }

    /**
     * Log a denied admin action. Convenience method that sets result=DENIED.
     */
    public void logDenied(HttpServletRequest request, String action,
                          String targetResourceType, String targetResourceId,
                          String targetTenantId) {
        log(request, action, targetResourceType, targetResourceId, targetTenantId, "DENIED");
    }

    // ==================== Stateless utility methods (safe as static) ====================

    static Map<String, String> sanitizeDetails(Map<String, String> details) {
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

    static String extractActor(HttpServletRequest request) {
        Object subject = request.getAttribute("jwt.subject");
        if (subject != null && !subject.toString().isBlank()) {
            return subject.toString();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
            return auth.getName();
        }
        return "anonymous";
    }

    static String extractRoles(HttpServletRequest request) {
        Object rolesAttr = request.getAttribute("jwt.roles");
        if (rolesAttr instanceof List<?> roles) {
            return String.join(",", roles.stream().map(Object::toString).toList());
        } else if (rolesAttr instanceof String rolesStr) {
            return rolesStr;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null) {
            return auth.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .reduce((a, b) -> a + "," + b)
                    .orElse("none");
        }
        return "none";
    }
}
