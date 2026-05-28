package com.example.platform.audit.api.dto;

/**
 * Query parameters for audit record listing.
 */
public record AuditRecordQuery(
        int page,
        int size,
        String category,
        String action,
        String actorType,
        String actorId,
        String resourceType,
        String resourceId,
        String targetTenantId,
        String result,
        String from,
        String to
) {
    public AuditRecordQuery {
        if (page < 0) page = 0;
        if (size <= 0) size = 50;
        if (size > 200) size = 200;
    }
}
