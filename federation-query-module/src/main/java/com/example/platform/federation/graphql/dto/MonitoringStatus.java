package com.example.platform.federation.graphql.dto;

public record MonitoringStatus(
        boolean sentryEnabled,
        boolean openReplayEnabled,
        String lastErrorAt,
        String lastFeedbackAt
) {}
