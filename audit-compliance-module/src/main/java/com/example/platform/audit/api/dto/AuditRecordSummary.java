package com.example.platform.audit.api.dto;

/**
 * Audit record summary for list views.
 */
public record AuditRecordSummary(
        String id,
        String createdAt,
        String category,
        String action,
        String actorType,
        String actorId,
        String resourceType,
        String resourceId,
        String targetTenantId,
        String result,
        String requestId,
        String traceId
) {}
