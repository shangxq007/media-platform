package com.example.platform.audit.api.dto;

import java.util.Map;

/**
 * Audit record detail for single-record views.
 */
public record AuditRecordDetail(
        String id,
        String createdAt,
        String category,
        String action,
        String actorType,
        String actorId,
        String resourceType,
        String resourceId,
        Map<String, Object> payload
) {}
