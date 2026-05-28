package com.example.platform.audit.api.dto;

/**
 * Paginated response for audit record queries.
 */
public record AuditRecordPage(
        java.util.List<AuditRecordSummary> items,
        int page,
        int size,
        long total
) {}
