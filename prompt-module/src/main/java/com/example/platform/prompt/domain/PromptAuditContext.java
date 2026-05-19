package com.example.platform.prompt.domain;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Audit context for prompt operations.
 */
public record PromptAuditContext(
        String auditId,
        String tenantId,
        String userId,
        String action,
        String resourceType,
        String resourceId,
        Map<String, Object> details,
        OffsetDateTime timestamp) {
}
