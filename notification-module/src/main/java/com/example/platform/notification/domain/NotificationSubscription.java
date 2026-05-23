package com.example.platform.notification.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record NotificationSubscription(
        String subscriptionId,
        String tenantId,
        String workspaceId,
        String userId,
        String eventKey,
        boolean enabled,
        List<String> channels,
        String frequency,
        Map<String, String> filters,
        String quietHoursStart,
        String quietHoursEnd,
        String quietHoursTimezone,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
