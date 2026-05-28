package com.example.platform.audit.app;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable security alert DTO passed through {@link SecurityAlertPort}.
 *
 * <p>Does NOT contain payload, raw audit records, or sensitive fields.
 * Attributes must only contain non-sensitive summary data.
 */
public record SecurityAlert(
    String rule,
    String severity,
    String category,
    String action,
    String actorType,
    String actorId,
    String resourceType,
    String resourceId,
    String targetTenantId,
    String result,
    String requestId,
    String traceId,
    Instant createdAt,
    Map<String, Object> attributes
) {
    /** Sensitive key names that must never appear in attributes. */
    private static final List<String> SENSITIVE_KEYS = List.of(
            "authorization", "cookie", "token", "accesstoken", "refreshtoken",
            "apikey", "api_key", "key", "secret", "password", "passwd",
            "signedurl", "signed_url", "virtualkey", "virtual_key",
            "litellmkey", "litellm_key", "bearer", "payload"
    );

    public SecurityAlert {
        if (rule == null) rule = "UNKNOWN";
        if (severity == null) severity = "MEDIUM";
        if (createdAt == null) createdAt = Instant.now();
        if (attributes == null) attributes = Map.of();
        // Sanitize attributes — remove any sensitive keys
        attributes = sanitizeAttributes(attributes);
    }

    public static SecurityAlert of(String rule, String severity, String category,
                                    String action, String actorType, String actorId,
                                    String resourceType, String resourceId,
                                    String targetTenantId, String result,
                                    String requestId, String traceId) {
        return new SecurityAlert(rule, severity, category, action, actorType, actorId,
                resourceType, resourceId, targetTenantId, result, requestId, traceId,
                Instant.now(), Map.of());
    }

    public static SecurityAlert withAttributes(String rule, String severity, String category,
                                                String action, String actorType, String actorId,
                                                String resourceType, String resourceId,
                                                String targetTenantId, String result,
                                                String requestId, String traceId,
                                                Map<String, Object> attributes) {
        return new SecurityAlert(rule, severity, category, action, actorType, actorId,
                resourceType, resourceId, targetTenantId, result, requestId, traceId,
                Instant.now(), attributes);
    }

    /**
     * Remove any sensitive keys from attributes map.
     */
    private static Map<String, Object> sanitizeAttributes(Map<String, Object> attrs) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : attrs.entrySet()) {
            String key = entry.getKey().toLowerCase().replace("-", "").replace("_", "");
            if (SENSITIVE_KEYS.contains(key)) {
                sanitized.put(entry.getKey(), "[REDACTED]");
            } else {
                sanitized.put(entry.getKey(), entry.getValue());
            }
        }
        return sanitized;
    }
}
