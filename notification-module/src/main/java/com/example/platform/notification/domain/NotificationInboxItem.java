package com.example.platform.notification.domain;

import java.time.OffsetDateTime;

public record NotificationInboxItem(
        String id,
        String tenantId,
        String workspaceId,
        String userId,
        String eventKey,
        String type,
        String title,
        String message,
        boolean read,
        String link,
        String actorId,
        String resourceType,
        String resourceId,
        OffsetDateTime createdAt,
        OffsetDateTime readAt
) {}
