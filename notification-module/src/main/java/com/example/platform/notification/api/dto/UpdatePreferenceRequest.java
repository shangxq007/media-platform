package com.example.platform.notification.api.dto;

import java.util.Map;

public record UpdatePreferenceRequest(
        Boolean globalEnabled,
        Map<String, Boolean> channelEnabled,
        Map<String, Boolean> eventEnabled,
        String quietHoursStart,
        String quietHoursEnd,
        String quietHoursTimezone,
        String digestMode,
        Boolean criticalOverride
) {}
