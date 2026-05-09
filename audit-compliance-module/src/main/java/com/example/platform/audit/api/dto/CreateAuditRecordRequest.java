package com.example.platform.audit.api.dto;

import com.example.platform.audit.app.AuditCategory;
import java.util.Map;

public record CreateAuditRecordRequest(
        String actorType,
        String actorId,
        String action,
        String resourceType,
        String resourceId,
        Map<String, Object> payload,
        AuditCategory category
) {}
