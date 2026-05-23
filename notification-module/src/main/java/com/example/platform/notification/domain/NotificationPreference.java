package com.example.platform.notification.domain;

import java.time.OffsetDateTime;
import java.util.Map;

public record NotificationPreference(
        String preferenceId,
        String tenantId,
        String workspaceId,
        String userId,
        boolean globalEnabled,
        Map<String, Boolean> channelEnabled,
        Map<String, Boolean> eventEnabled,
        String quietHoursStart,
        String quietHoursEnd,
        String quietHoursTimezone,
        String digestMode,
        boolean criticalOverride,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
