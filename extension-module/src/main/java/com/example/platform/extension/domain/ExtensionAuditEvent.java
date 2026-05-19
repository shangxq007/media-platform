package com.example.platform.extension.domain;

import java.time.OffsetDateTime;

public record ExtensionAuditEvent(
        String id,
        String extensionCode,
        String extensionVersion,
        String eventType,
        String actor,
        String tenantId,
        String userId,
        String traceId,
        String trustLevel,
        String details,
        String severity,
        OffsetDateTime createdAt
) {
    public enum Severity { INFO, WARN, ERROR, CRITICAL }

    public enum EventType {
        EXTENSION_REGISTERED,
        EXTENSION_UNLOADED,
        EXTENSION_UPGRADED,
        EXTENSION_ROLLED_BACK,
        EXTENSION_EXECUTION_STARTED,
        EXTENSION_EXECUTION_COMPLETED,
        EXTENSION_EXECUTION_TIMEOUT,
        EXTENSION_EXECUTION_FAILED,
        ROUTING_RULE_CREATED,
        ROUTING_RULE_UPDATED,
        ROUTING_RULE_DELETED,
        ROUTING_RULE_ROLLED_BACK,
        RESOURCE_LIMIT_EXCEEDED,
        RESOURCE_LIMIT_UPDATED,
        ROLLBACK_POINT_CREATED,
        SECURITY_VIOLATION,
        REVIEW_REQUIRED,
        REVIEW_APPROVED,
        REVIEW_REJECTED
    }

    public ExtensionAuditEvent {
        if (extensionCode == null || extensionCode.isBlank()) {
            throw new IllegalArgumentException("extensionCode must not be blank");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("actor must not be blank");
        }
        severity = severity != null ? severity : Severity.INFO.name();
    }
}
